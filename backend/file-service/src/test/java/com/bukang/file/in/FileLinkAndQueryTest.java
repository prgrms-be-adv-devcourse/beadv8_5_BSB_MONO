package com.bukang.file.in;

import static com.bukang.common.shared.file.domain.FileType.DETAIL;
import static com.bukang.common.shared.file.domain.FileType.THUMBNAIL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.bukang.common.shared.file.domain.FileType;
import com.bukang.file.domain.FileFormat;
import com.bukang.file.domain.FileStatus;
import com.bukang.file.domain.StoredFile;
import com.bukang.file.out.StoredFileRepository;
import com.bukang.file.support.FakeFileStorageConfig;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(FakeFileStorageConfig.class)
class FileLinkAndQueryTest {
	private static final int OWNER_ID = 1;
	private static final int OTHER_MEMBER_ID = 2;
	private static final String RACE = "Race";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private StoredFileRepository storedFileRepository;

	@AfterEach
	void tearDown() {
		storedFileRepository.deleteAll();
	}

	@Test
	@DisplayName("업로드를 마친 파일을 대상에 연결하면 ACTIVE가 되고 용도·순서대로 돌려준다")
	void link() throws Exception {
		StoredFile thumbnail = uploadedFile(OWNER_ID, THUMBNAIL);
		StoredFile detail = uploadedFile(OWNER_ID, DETAIL);

		link(RACE, 1, OWNER_ID, item(detail, "DETAIL", 0), item(thumbnail, "THUMBNAIL", 0))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("파일을 연결했습니다."))
			.andExpect(jsonPath("$.data.length()").value(2))
			.andExpect(jsonPath("$.data[0].id").value(thumbnail.getId()))
			.andExpect(jsonPath("$.data[0].fileType").value("THUMBNAIL"))
			.andExpect(jsonPath("$.data[0].status").value("ACTIVE"))
			.andExpect(jsonPath("$.data[0].refType").value(RACE))
			.andExpect(jsonPath("$.data[0].refId").value(1))
			.andExpect(jsonPath("$.data[0].url").isNotEmpty())
			.andExpect(jsonPath("$.data[1].id").value(detail.getId()))
			.andExpect(jsonPath("$.data[1].fileType").value("DETAIL"));

		StoredFile linked = storedFileRepository.findById(thumbnail.getId()).orElseThrow();
		assertThat(linked.getStatus()).isEqualTo(FileStatus.ACTIVE);
		assertThat(linked.getFileType()).isEqualTo(THUMBNAIL);
	}

	@Test
	@DisplayName("같은 요청을 다시 보내도 결과가 같다")
	void linkTwice() throws Exception {
		StoredFile thumbnail = uploadedFile(OWNER_ID, THUMBNAIL);
		link(RACE, 1, OWNER_ID, item(thumbnail, "THUMBNAIL", 0)).andExpect(status().isOk());

		link(RACE, 1, OWNER_ID, item(thumbnail, "THUMBNAIL", 0))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].status").value("ACTIVE"));
	}

	@Test
	@DisplayName("목록에서 빠진 기존 파일은 DELETED가 되고, 남은 파일은 순서가 바뀐다")
	void replace() throws Exception {
		StoredFile first = uploadedFile(OWNER_ID, DETAIL);
		StoredFile second = uploadedFile(OWNER_ID, DETAIL);
		StoredFile third = uploadedFile(OWNER_ID, DETAIL);
		link(RACE, 1, OWNER_ID, item(first, "DETAIL", 0), item(second, "DETAIL", 1)).andExpect(status().isOk());

		link(RACE, 1, OWNER_ID, item(second, "DETAIL", 0), item(third, "DETAIL", 1))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[0].id").value(second.getId()))
			.andExpect(jsonPath("$.data[1].id").value(third.getId()));

		assertThat(statusOf(first)).isEqualTo(FileStatus.DELETED);
		assertThat(storedFileRepository.findById(second.getId()).orElseThrow().getSortNo()).isZero();
	}

	@Test
	@DisplayName("빈 목록을 보내면 대상의 파일을 모두 뗀다")
	void unlinkAll() throws Exception {
		StoredFile thumbnail = uploadedFile(OWNER_ID, THUMBNAIL);
		link(RACE, 1, OWNER_ID, item(thumbnail, "THUMBNAIL", 0)).andExpect(status().isOk());

		link(RACE, 1, OWNER_ID)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(0));

		assertThat(statusOf(thumbnail)).isEqualTo(FileStatus.DELETED);
	}

	@Test
	@DisplayName("소유자가 올리지 않은 파일은 403이고 아무것도 바뀌지 않는다")
	void rejectOtherOwner() throws Exception {
		StoredFile mine = uploadedFile(OWNER_ID, THUMBNAIL);
		StoredFile others = uploadedFile(OTHER_MEMBER_ID, DETAIL);

		link(RACE, 1, OWNER_ID, item(mine, "THUMBNAIL", 0), item(others, "DETAIL", 0))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value("본인이 올린 파일만 처리할 수 있습니다. (fileId: %d, fileType: DETAIL, sortNo: 0)"
				.formatted(others.getId())));

		assertThat(statusOf(mine)).isEqualTo(FileStatus.UPLOADED);
	}

	@Test
	@DisplayName("업로드 확인 전 파일은 409")
	void rejectPendingFile() throws Exception {
		StoredFile pending = storedFileRepository.save(pendingFile(OWNER_ID, THUMBNAIL));

		link(RACE, 1, OWNER_ID, item(pending, "THUMBNAIL", 0))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("업로드가 확인되지 않은 파일입니다. (fileId: %d, fileType: THUMBNAIL, sortNo: 0)"
				.formatted(pending.getId())));
	}

	@Test
	@DisplayName("다른 대상에 연결된 파일은 409")
	void rejectLinkedElsewhere() throws Exception {
		StoredFile thumbnail = uploadedFile(OWNER_ID, THUMBNAIL);
		link(RACE, 1, OWNER_ID, item(thumbnail, "THUMBNAIL", 0)).andExpect(status().isOk());

		link(RACE, 2, OWNER_ID, item(thumbnail, "THUMBNAIL", 0))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("다른 대상에 연결된 파일입니다. (fileId: %d, fileType: THUMBNAIL, sortNo: 0)"
				.formatted(thumbnail.getId())));
	}

	@Test
	@DisplayName("용도마다 정해진 개수를 넘으면 400이고 아무것도 바뀌지 않는다")
	void rejectTooManyFiles() throws Exception {
		StoredFile first = uploadedFile(OWNER_ID, THUMBNAIL);
		StoredFile second = uploadedFile(OWNER_ID, THUMBNAIL);

		link(RACE, 1, OWNER_ID, item(first, "THUMBNAIL", 0), item(second, "THUMBNAIL", 1))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("대표 이미지 최대 개수(1개)를 넘었습니다."));

		assertThat(statusOf(first)).isEqualTo(FileStatus.UPLOADED);
		assertThat(statusOf(second)).isEqualTo(FileStatus.UPLOADED);
	}

	@Test
	@DisplayName("업로드할 때 정한 용도와 다른 용도로 연결하면 400이고, 업로드 때 용도를 알려 준다")
	void rejectFileTypeMismatch() throws Exception {
		StoredFile thumbnail = uploadedFile(OWNER_ID, THUMBNAIL);

		link(RACE, 1, OWNER_ID, item(thumbnail, "DETAIL", 0))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(
				"업로드할 때 정한 파일 용도(THUMBNAIL)와 다릅니다. (fileId: %d, fileType: DETAIL, sortNo: 0)"
					.formatted(thumbnail.getId())));

		assertThat(statusOf(thumbnail)).isEqualTo(FileStatus.UPLOADED);
	}

	@Test
	@DisplayName("없거나 삭제된 파일은 404")
	void rejectMissingFile() throws Exception {
		link(RACE, 1, OWNER_ID, "{\"fileId\": 9999, \"fileType\": \"THUMBNAIL\", \"sortNo\": 0}")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("존재하지 않는 파일입니다. (fileId: 9999, fileType: THUMBNAIL, sortNo: 0)"));
	}

	@Test
	@DisplayName("요청 값이 올바르지 않으면 400")
	void rejectInvalidRequest() throws Exception {
		StoredFile file = uploadedFile(OWNER_ID, THUMBNAIL);

		link(RACE, 1, OWNER_ID, item(file, "thumbnail", 0))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("요청 본문 형식이 올바르지 않습니다."));
		link(RACE, 1, OWNER_ID, item(file, "THUMBNAIL", 0), item(file, "DETAIL", 1))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("같은 파일을 두 번 연결할 수 없습니다. (fileId: %d, fileType: DETAIL, sortNo: 1)"
				.formatted(file.getId())));
		link("race-event", 1, OWNER_ID, item(file, "THUMBNAIL", 0))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("대상 종류는 대문자로 시작하는 영문자·숫자 50자 이하로 작성해주세요."));
		// 클래스 이름이 아닌 소문자 시작 값
		link("race", 1, OWNER_ID, item(file, "THUMBNAIL", 0))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("대상 종류는 대문자로 시작하는 영문자·숫자 50자 이하로 작성해주세요."));
		link(RACE, 0, OWNER_ID, item(file, "THUMBNAIL", 0))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("대상 ID는 0보다 커야 합니다."));
		mockMvc.perform(put("/internal/v1/file/refs/{refType}/{refId}/files", RACE, 1)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"files\": []}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("소유자 회원 ID를 작성해주세요."));
	}

	@Test
	@DisplayName("대상별 조회는 연결된 파일만 용도·순서대로 돌려준다")
	void findByRef() throws Exception {
		StoredFile thumbnail = uploadedFile(OWNER_ID, THUMBNAIL);
		StoredFile detail = uploadedFile(OWNER_ID, DETAIL);
		uploadedFile(OWNER_ID, DETAIL);
		link(RACE, 1, OWNER_ID, item(detail, "DETAIL", 0), item(thumbnail, "THUMBNAIL", 0))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/file/files").param("refType", RACE).param("refId", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("파일 목록을 조회했습니다."))
			.andExpect(jsonPath("$.data.length()").value(2))
			.andExpect(jsonPath("$.data[0].fileType").value("THUMBNAIL"))
			.andExpect(jsonPath("$.data[1].fileType").value("DETAIL"));

		mockMvc.perform(get("/api/v1/file/files").param("refType", RACE))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("필수 요청 값이 없습니다."));
	}

	@Test
	@DisplayName("연결된 파일은 누구나, 연결 전 파일은 올린 회원만 조회한다")
	void findById() throws Exception {
		StoredFile linked = uploadedFile(OWNER_ID, THUMBNAIL);
		link(RACE, 1, OWNER_ID, item(linked, "THUMBNAIL", 0)).andExpect(status().isOk());
		StoredFile notLinked = uploadedFile(OWNER_ID, DETAIL);
		StoredFile pending = storedFileRepository.save(pendingFile(OWNER_ID, DETAIL));

		mockMvc.perform(get("/api/v1/file/files/{fileId}", linked.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("파일을 조회했습니다."))
			.andExpect(jsonPath("$.data.status").value("ACTIVE"));
		mockMvc.perform(get("/api/v1/file/files/{fileId}", notLinked.getId())
				.header("X-Member-Id", OWNER_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.url").isNotEmpty());
		mockMvc.perform(get("/api/v1/file/files/{fileId}", notLinked.getId())
				.header("X-Member-Id", OTHER_MEMBER_ID))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value("연결된 파일이 아닙니다(현재 상태: 업로드 완료). 본인이 올린 파일만 볼 수 있습니다."));
		mockMvc.perform(get("/api/v1/file/files/{fileId}", notLinked.getId()))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/file/files/{fileId}", pending.getId())
				.header("X-Member-Id", OWNER_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.url").doesNotExist());
		mockMvc.perform(get("/api/v1/file/files/{fileId}", 9999))
			.andExpect(status().isNotFound());
	}

	private ResultActions link(String refType, int refId, int ownerId, String... items) throws Exception {
		String body = """
			{"ownerId": %d, "files": [%s]}
			""".formatted(ownerId, String.join(",", items));
		return mockMvc.perform(put("/internal/v1/file/refs/{refType}/{refId}/files", refType, refId)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private String item(StoredFile file, String fileType, int sortNo) {
		return """
			{"fileId": %d, "fileType": "%s", "sortNo": %d}
			""".formatted(file.getId(), fileType, sortNo);
	}

	private FileStatus statusOf(StoredFile file) {
		return storedFileRepository.findById(file.getId()).orElseThrow().getStatus();
	}

	private StoredFile pendingFile(int ownerId, FileType fileType) {
		return StoredFile.pending(
			"uploads/" + UUID.randomUUID() + ".png", "race.png", fileType, FileFormat.PNG, 70, ownerId);
	}

	// 업로드 확인까지 마친 파일
	private StoredFile uploadedFile(int ownerId, FileType fileType) {
		StoredFile file = pendingFile(ownerId, fileType);
		file.confirmUpload(file.getS3Key().replace("uploads/", "files/"));
		return storedFileRepository.save(file);
	}
}
