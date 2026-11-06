package com.bukang.file.in;

import static com.bukang.common.shared.file.domain.FileType.DETAIL;
import static com.bukang.common.shared.file.domain.FileType.THUMBNAIL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.bukang.common.global.exception.BusinessException;
import com.bukang.common.global.json.JsonConverter;
import com.bukang.common.shared.file.domain.FileType;
import com.bukang.common.shared.file.event.FileLinkRequestedEvent;
import com.bukang.file.domain.FileFormat;
import com.bukang.file.domain.FileStatus;
import com.bukang.file.domain.StoredFile;
import com.bukang.file.out.FileInboxKafkaEventRepository;
import com.bukang.file.out.StoredFileRepository;
import com.bukang.file.support.FakeFileStorageConfig;

/**
 * FileKafkaConsumer가 상품의 파일 연결 요청을 받아, 인박스로 같은 메시지를 한 번만 처리하는지 확인한다.
 * 메시지는 상품이 보내는 것과 같은 JSON 문자열(이벤트를 JsonConverter로 바꾼 값)로 만들고,
 * message-id 헤더 값은 리스너 메서드의 인자로 직접 넘긴다 (실제 브로커로 헤더가 오는지는 QueueKafkaInboxTest가 확인한다).
 * - 연결 요청은 "보낸 목록이 대상의 파일 전체"라 비교할 수정 일시가 없다. 이미 처리한 옛 메시지가 다시 오면 인박스만 막는다
 * - 연결이 거절되면 인박스 행도 함께 롤백되어, DLT에서 같은 헤더로 다시 보냈을 때 처리할 수 있다
 * 재시도·DLT 토픽으로 보내는 일은 Kafka가 하므로, 여기서는 어떤 예외가 나는지까지만 본다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(FakeFileStorageConfig.class)
class FileKafkaConsumerTest {
	private static final long OWNER_ID = 1;
	private static final long OTHER_MEMBER_ID = 2;
	private static final String RACE = "Race";
	private static final long RACE_ID = 1;
	// 상품 아웃박스가 붙이는 message-id ({서비스}-outbox-{UUID}). 서로 다른 메시지 A·B
	private static final String MESSAGE_ID_A = "product-outbox-3b241101-e2bb-4255-8caf-4136c566aa01";
	private static final String MESSAGE_ID_B = "product-outbox-3b241101-e2bb-4255-8caf-4136c566ab02";

	@Autowired
	private FileKafkaConsumer fileKafkaConsumer;
	@Autowired
	private JsonConverter jsonConverter;
	@Autowired
	private StoredFileRepository storedFileRepository;
	@Autowired
	private FileInboxKafkaEventRepository fileInboxKafkaEventRepository;

	@AfterEach
	void tearDown() {
		storedFileRepository.deleteAll();
		fileInboxKafkaEventRepository.deleteAll();
	}

	@Test
	@DisplayName("handleFileLinkRequested: 같은 message-id(A)의 연결 요청이 다시 오면 인박스가 건너뛰어, "
		+ "썸네일은 ACTIVE 그대로이고 인박스는 1행이다")
	void sameLinkMessageTwiceIsSkippedByInbox() {
		StoredFile thumbnail = uploadedFile(OWNER_ID, THUMBNAIL);
		String message = linkMessage(OWNER_ID, item(thumbnail, 0));

		fileKafkaConsumer.handleFileLinkRequested(message, MESSAGE_ID_A);
		fileKafkaConsumer.handleFileLinkRequested(message, MESSAGE_ID_A);   // 재전달 (인박스가 건너뛴다)

		assertThat(statusOf(thumbnail)).isEqualTo(FileStatus.ACTIVE);
		assertThat(fileInboxKafkaEventRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("handleFileLinkRequested: 상세 이미지를 [가, 나] 순서로 연결(A)하고 [나, 가]로 바꾼(B) 뒤 "
		+ "첫 메시지가 다시 오면, 이미 처리한 메시지라 인박스가 건너뛰어 [나, 가] 그대로다 "
		+ "(헤더가 없으면 재전달된 첫 메시지가 [가, 나]로 되돌린다)")
	void redeliveredOldLinkMessageIsSkippedByInbox() {
		StoredFile first = uploadedFile(OWNER_ID, DETAIL);
		StoredFile second = uploadedFile(OWNER_ID, DETAIL);
		String firstOrder = linkMessage(OWNER_ID, item(first, 0), item(second, 1));
		String changedOrder = linkMessage(OWNER_ID, item(second, 0), item(first, 1));

		fileKafkaConsumer.handleFileLinkRequested(firstOrder, MESSAGE_ID_A);
		fileKafkaConsumer.handleFileLinkRequested(changedOrder, MESSAGE_ID_B);
		fileKafkaConsumer.handleFileLinkRequested(firstOrder, MESSAGE_ID_A);   // 재전달 (인박스가 건너뛴다)

		assertThat(sortNoOf(second)).isZero();
		assertThat(sortNoOf(first)).isEqualTo(1);
		assertThat(fileInboxKafkaEventRepository.count()).isEqualTo(2);
	}

	@Test
	@DisplayName("handleFileLinkRequested: 남의 파일을 연결하라는 메시지는 BusinessException(바로 DLT)이 나고, "
		+ "연결과 함께 롤백되어 인박스에 남지 않는다 (DLT에서 같은 헤더로 다시 보내면 처리할 수 있다)")
	void rejectedLinkLeavesNoInboxRow() {
		StoredFile others = uploadedFile(OTHER_MEMBER_ID, THUMBNAIL);
		String message = linkMessage(OWNER_ID, item(others, 0));

		assertThatThrownBy(() -> fileKafkaConsumer.handleFileLinkRequested(message, MESSAGE_ID_A))
			.isInstanceOf(BusinessException.class);

		assertThat(statusOf(others)).isEqualTo(FileStatus.UPLOADED);
		assertThat(fileInboxKafkaEventRepository.count()).isZero();
	}

	// 상품이 보내는 연결 요청 (대회 RACE_ID 하나에 파일 목록)
	private String linkMessage(long ownerId, FileLinkRequestedEvent.Item... items) {
		return jsonConverter.toJson(new FileLinkRequestedEvent(ownerId,
			List.of(new FileLinkRequestedEvent.Ref(RACE, RACE_ID, List.of(items)))));
	}

	// 업로드할 때 정한 용도 그대로 연결한다
	private FileLinkRequestedEvent.Item item(StoredFile file, int sortNo) {
		return new FileLinkRequestedEvent.Item(file.getId(), file.getFileType(), sortNo);
	}

	private FileStatus statusOf(StoredFile file) {
		return storedFileRepository.findById(file.getId()).orElseThrow().getStatus();
	}

	private int sortNoOf(StoredFile file) {
		return storedFileRepository.findById(file.getId()).orElseThrow().getSortNo();
	}

	// 업로드 확인까지 마친 파일 (FileLinkAndQueryTest와 같은 방법)
	private StoredFile uploadedFile(long ownerId, FileType fileType) {
		StoredFile file = StoredFile.pending(
			"uploads/" + UUID.randomUUID() + ".png", "race.png", fileType, FileFormat.PNG, 70, ownerId);
		file.confirmUpload(file.getS3Key().replace("uploads/", "files/"));
		return storedFileRepository.save(file);
	}
}
