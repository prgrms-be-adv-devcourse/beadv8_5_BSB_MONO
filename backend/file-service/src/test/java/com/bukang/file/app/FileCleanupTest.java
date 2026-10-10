package com.bukang.file.app;

import static com.bukang.common.shared.file.domain.FileType.THUMBNAIL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.file.domain.FileFormat;
import com.bukang.file.domain.FileStatus;
import com.bukang.file.domain.StoredFile;
import com.bukang.file.out.StoredFileRepository;
import com.bukang.file.support.FakeFileStorage;
import com.bukang.file.support.FakeFileStorageConfig;

/**
 * 정리 배치의 삭제 로직(FileFacade.cleanupMore).
 * DB 시각은 고치지 않고, 배치가 도는 시각(runDateTime)을 미래로 옮겨 "1일 지남"을 만든다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(FakeFileStorageConfig.class)
class FileCleanupTest {
	private static final int OWNER_ID = 1;
	private static final int LIMIT = 100;
	private static final String RACE = "Race";
	private static final byte[] BODY = {0x01};

	@Autowired
	private FileFacade fileFacade;

	@Autowired
	private StoredFileRepository storedFileRepository;

	@Autowired
	private FakeFileStorage fakeFileStorage;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@AfterEach
	void tearDown() {
		storedFileRepository.deleteAll();
		fakeFileStorage.clear();
	}

	@Test
	@DisplayName("마지막으로 바뀐 뒤 1일이 지난 PENDING·UPLOADED·DELETED 파일은 행과 S3 객체가 지워진다")
	void deleteOldFiles() {
		StoredFile pending = saveWithObject(pendingFile());
		StoredFile uploaded = saveWithObject(uploadedFile());
		StoredFile deleted = saveWithObject(deletedFile());

		int deletedCount = fileFacade.cleanupMore(LIMIT, cutoffAt(LocalDateTime.now().plusDays(2)));

		assertThat(deletedCount).isEqualTo(3);
		assertThat(storedFileRepository.findAllById(List.of(pending.getId(), uploaded.getId(), deleted.getId())))
			.isEmpty();
		assertThat(fakeFileStorage.exists(pending.getS3Key())).isFalse();
		assertThat(fakeFileStorage.exists(uploaded.getS3Key())).isFalse();
		assertThat(fakeFileStorage.exists(deleted.getS3Key())).isFalse();
	}

	@Test
	@DisplayName("대상에 연결된 파일(ACTIVE)은 오래돼도 지우지 않는다")
	void keepActiveFile() {
		StoredFile active = saveWithObject(activeFile());

		int deletedCount = fileFacade.cleanupMore(LIMIT, cutoffAt(LocalDateTime.now().plusDays(2)));

		assertThat(deletedCount).isZero();
		assertThat(storedFileRepository.findById(active.getId())).isPresent();
		assertThat(fakeFileStorage.exists(active.getS3Key())).isTrue();
	}

	@Test
	@DisplayName("마지막으로 바뀐 뒤 1일이 안 된 파일은 지우지 않는다")
	void keepRecentFile() {
		StoredFile uploaded = saveWithObject(uploadedFile());

		int deletedCount = fileFacade.cleanupMore(LIMIT, cutoffAt(LocalDateTime.now().plusHours(12)));

		assertThat(deletedCount).isZero();
		assertThat(storedFileRepository.findById(uploaded.getId())).isPresent();
		assertThat(fakeFileStorage.exists(uploaded.getS3Key())).isTrue();
	}

	@Test
	@DisplayName("S3 삭제가 일부 실패해도 행은 지워지고, 나머지 객체도 지워진다")
	void partialObjectDeleteFailure() {
		StoredFile failing = saveWithObject(uploadedFile());
		StoredFile other = saveWithObject(uploadedFile());
		fakeFileStorage.failOnDelete(failing.getS3Key());

		int deletedCount = fileFacade.cleanupMore(LIMIT, cutoffAt(LocalDateTime.now().plusDays(2)));

		assertThat(deletedCount).isEqualTo(2);
		assertThat(storedFileRepository.findAllById(List.of(failing.getId(), other.getId()))).isEmpty();
		// 지우지 못한 객체는 행 없이 S3에만 남는다 (로그로 찾는다)
		assertThat(fakeFileStorage.exists(failing.getS3Key())).isTrue();
		assertThat(fakeFileStorage.exists(other.getS3Key())).isFalse();
	}

	@Test
	@DisplayName("S3 삭제 요청 자체가 실패하면 예외가 나지만, 행 삭제는 그 전에 이미 커밋되어 있다")
	void rowsCommittedBeforeObjectDelete() {
		StoredFile uploaded = saveWithObject(uploadedFile());
		fakeFileStorage.failDeleteRequest();

		assertThatThrownBy(() -> fileFacade.cleanupMore(LIMIT, cutoffAt(LocalDateTime.now().plusDays(2))))
			.isInstanceOf(IllegalStateException.class);

		// S3 삭제가 행 삭제와 같은 트랜잭션이었다면 예외로 롤백되어 행이 남았을 것이다
		assertThat(storedFileRepository.findById(uploaded.getId())).isEmpty();
		assertThat(fakeFileStorage.exists(uploaded.getS3Key())).isTrue();
	}

	@Test
	@DisplayName("배치가 후보로 읽은 뒤 대상에 연결된 파일은 조건부 삭제가 0건이라 지우지 않는다")
	void keepFileChangedAfterRead() {
		StoredFile file = saveWithObject(uploadedFile());
		// 배치는 UPLOADED 상태로 읽었고, 지우기 전에 link가 ACTIVE로 바꿔 커밋했다고 본다
		FileStatus readStatus = file.getStatus();
		file.linkTo(RACE, 1, 0);
		storedFileRepository.save(file);
		LocalDateTime cutoff = cutoffAt(LocalDateTime.now().plusDays(2));

		Integer deletedRows = transactionTemplate.execute(status ->
			storedFileRepository.deleteIfOld(file.getId(), readStatus, cutoff));

		assertThat(deletedRows).isZero();
		assertThat(storedFileRepository.findById(file.getId()).orElseThrow().getStatus()).isEqualTo(FileStatus.ACTIVE);
	}

	@Test
	@DisplayName("한 번에 limit건만 지우고, 다시 부르면 남은 파일을 지운다 (늘 첫 페이지를 읽어도 건너뛰는 파일이 없다)")
	void deleteByLimit() {
		List<Integer> ids = List.of(
			saveWithObject(uploadedFile()).getId(),
			saveWithObject(uploadedFile()).getId(),
			saveWithObject(uploadedFile()).getId());
		// 한 번 실행하는 동안 기준 시각은 같다
		LocalDateTime cutoff = cutoffAt(LocalDateTime.now().plusDays(2));

		assertThat(fileFacade.cleanupMore(2, cutoff)).isEqualTo(2);
		assertThat(fileFacade.cleanupMore(2, cutoff)).isEqualTo(1);
		assertThat(fileFacade.cleanupMore(2, cutoff)).isZero();
		assertThat(storedFileRepository.findAllById(ids)).isEmpty();
	}

	// 배치가 runDateTime에 돌 때의 기준 시각 (보관 기간 1일, FileCleanupBatchJobConfig와 같다)
	private LocalDateTime cutoffAt(LocalDateTime runDateTime) {
		return runDateTime.minusDays(1);
	}

	// 행을 저장하고, 그 키에 S3 객체도 올려 둔다
	private StoredFile saveWithObject(StoredFile file) {
		StoredFile saved = storedFileRepository.save(file);
		fakeFileStorage.upload(saved.getS3Key(), BODY, "image/png");
		return saved;
	}

	// 업로드 URL만 받은 파일
	private StoredFile pendingFile() {
		return StoredFile.pending(
			"uploads/" + UUID.randomUUID() + ".png", "race.png", THUMBNAIL, FileFormat.PNG, BODY.length, OWNER_ID);
	}

	// 업로드 확인까지 마친 파일
	private StoredFile uploadedFile() {
		StoredFile file = pendingFile();
		file.confirmUpload(file.getS3Key().replace("uploads/", "files/"));
		return file;
	}

	// 대상에 연결된 파일
	private StoredFile activeFile() {
		StoredFile file = uploadedFile();
		file.linkTo(RACE, 1, 0);
		return file;
	}

	// 다른 파일로 교체되어 연결이 끊긴 파일
	private StoredFile deletedFile() {
		StoredFile file = uploadedFile();
		file.delete();
		return file;
	}
}
