package com.bukang.file.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.bukang.file.domain.FileStatus;
import com.bukang.file.domain.StoredFile;
import com.bukang.file.out.StoredFileRepository;
import com.bukang.file.support.FakeFileStorage;
import com.bukang.file.support.FakeFileStorageConfig;
import com.bukang.file.support.TestImages;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(FakeFileStorageConfig.class)
class ApiV1FileUploadTest {
	private static final int MEMBER_ID = 1;
	private static final int OTHER_MEMBER_ID = 2;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private StoredFileRepository storedFileRepository;

	@Autowired
	private FakeFileStorage fakeFileStorage;

	@AfterEach
	void tearDown() {
		storedFileRepository.deleteAll();
		fakeFileStorage.clear();
	}

	@Test
	@DisplayName("업로드 URL을 발급하면 PENDING 파일이 생기고, PUT으로 올릴 URL과 헤더를 받는다")
	void issueUploadUrl() throws Exception {
		ResultActions result = issue(MEMBER_ID, "C:\\fakepath\\race-thumbnail.png", "image/png", 204800)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value(201))
			.andExpect(jsonPath("$.message").value("업로드 URL을 발급했습니다."))
			.andExpect(jsonPath("$.data.method").value("PUT"))
			.andExpect(jsonPath("$.data.headers.content-type").value("image/png"))
			.andExpect(jsonPath("$.data.uploadUrl").isNotEmpty())
			.andExpect(jsonPath("$.data.expiresAt").isNotEmpty());

		int fileId = fileIdOf(result);
		result.andExpect(header().string("Location", "/api/v1/file/files/" + fileId));

		StoredFile file = storedFileRepository.findById(fileId).orElseThrow();
		assertThat(file.getStatus()).isEqualTo(FileStatus.PENDING);
		assertThat(file.getS3Key()).startsWith("uploads/").endsWith(".png");
		assertThat(file.getOriginFileNm()).isEqualTo("race-thumbnail.png");
		assertThat(file.getRegistUser()).isEqualTo(MEMBER_ID);
		assertThat(file.getRefType()).isNull();
		assertThat(file.getRefId()).isNull();
	}

	@Test
	@DisplayName("JPG·PNG·WebP가 아니면 업로드 URL을 발급하지 않는다")
	void rejectUnsupportedType() throws Exception {
		issue(MEMBER_ID, "race.gif", "image/gif", 1024)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("JPG, PNG, WebP 이미지만 올릴 수 있습니다."));
		assertThat(storedFileRepository.count()).isZero();
	}

	@Test
	@DisplayName("10MB를 넘는 파일은 업로드 URL을 발급하지 않는다")
	void rejectTooLarge() throws Exception {
		issue(MEMBER_ID, "race.png", "image/png", 10L * 1024 * 1024 + 1)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("이미지는 10MB 이하만 올릴 수 있습니다."));
	}

	@Test
	@DisplayName("파일명이 비어 있으면 400")
	void rejectBlankFileName() throws Exception {
		issue(MEMBER_ID, " ", "image/png", 1024)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("파일명을 작성해주세요."));
	}

	@Test
	@DisplayName("회원 ID 헤더가 없으면 401")
	void rejectWithoutMemberId() throws Exception {
		mockMvc.perform(post("/api/v1/file/files")
				.contentType(MediaType.APPLICATION_JSON)
				.content(uploadRequest("race.png", "image/png", 1024)))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("로그인이 필요합니다."));
	}

	@Test
	@DisplayName("올린 파일이 신고한 형식·크기와 같으면 확정 경로로 옮기고 UPLOADED가 되며 이미지 URL을 받는다")
	void completeUpload() throws Exception {
		int fileId = issueAndUpload(TestImages.PNG, "image/png", TestImages.PNG);
		String uploadKey = s3KeyOf(fileId);

		complete(MEMBER_ID, fileId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("업로드를 확인했습니다."))
			.andExpect(jsonPath("$.data.id").value(fileId))
			.andExpect(jsonPath("$.data.status").value("UPLOADED"))
			.andExpect(jsonPath("$.data.contentType").value("image/png"))
			.andExpect(jsonPath("$.data.url").isNotEmpty());

		StoredFile file = storedFileRepository.findById(fileId).orElseThrow();
		assertThat(file.getStatus()).isEqualTo(FileStatus.UPLOADED);
		assertThat(file.getS3Key()).isEqualTo(uploadKey.replace("uploads/", "files/"));
		assertThat(fakeFileStorage.exists(uploadKey)).isFalse();
		assertThat(fakeFileStorage.bodyOf(file.getS3Key())).containsExactly(TestImages.PNG);
	}

	@Test
	@DisplayName("확인을 마친 뒤 같은 업로드 URL로 다른 내용을 올려도 확정 파일은 바뀌지 않는다")
	void reuploadAfterComplete() throws Exception {
		int fileId = issueAndUpload(TestImages.PNG, "image/png", TestImages.PNG);
		String uploadKey = s3KeyOf(fileId);
		complete(MEMBER_ID, fileId).andExpect(status().isOk());

		// 만료 전의 업로드 URL로 다시 올린 상황
		fakeFileStorage.upload(uploadKey, TestImages.TEXT, "image/png");

		complete(MEMBER_ID, fileId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("UPLOADED"));
		assertThat(fakeFileStorage.bodyOf(s3KeyOf(fileId))).containsExactly(TestImages.PNG);
	}

	@Test
	@DisplayName("WebP도 앞부분으로 형식을 확인한다")
	void completeWebpUpload() throws Exception {
		int fileId = issueAndUpload(TestImages.WEBP, "image/webp", TestImages.WEBP);

		complete(MEMBER_ID, fileId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("UPLOADED"));
	}

	@Test
	@DisplayName("이미 확인한 파일을 다시 완료하면 같은 결과를 돌려준다")
	void completeTwice() throws Exception {
		int fileId = issueAndUpload(TestImages.JPEG, "image/jpeg", TestImages.JPEG);
		complete(MEMBER_ID, fileId).andExpect(status().isOk());

		complete(MEMBER_ID, fileId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("UPLOADED"));
	}

	@Test
	@DisplayName("아직 올리지 않았으면 400이고, 파일은 PENDING으로 남아 다시 시도할 수 있다")
	void completeBeforeUpload() throws Exception {
		int fileId = fileIdOf(issue(MEMBER_ID, "race.png", "image/png", TestImages.PNG.length));

		complete(MEMBER_ID, fileId)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("업로드된 파일이 없습니다. 업로드 URL로 파일을 먼저 올려주세요."));

		assertThat(storedFileRepository.findById(fileId).orElseThrow().getStatus()).isEqualTo(FileStatus.PENDING);
	}

	@Test
	@DisplayName("확장자만 바꾼 파일은 거절하고, 올라간 객체를 모두 지운 뒤 DELETED로 남긴다")
	void rejectDisguisedFile() throws Exception {
		int fileId = issueAndUpload(TestImages.TEXT, "image/png", TestImages.TEXT);
		String uploadKey = s3KeyOf(fileId);

		complete(MEMBER_ID, fileId)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("올린 파일이 신고한 형식이나 크기와 다릅니다. 업로드 URL을 다시 받아 올려주세요."));

		assertThat(storedFileRepository.findById(fileId).orElseThrow().getStatus()).isEqualTo(FileStatus.DELETED);
		assertThat(fakeFileStorage.exists(uploadKey)).isFalse();
		assertThat(fakeFileStorage.exists(uploadKey.replace("uploads/", "files/"))).isFalse();
	}

	@Test
	@DisplayName("신고한 크기와 다른 파일을 올리면 거절한다")
	void rejectSizeMismatch() throws Exception {
		int fileId = fileIdOf(issue(MEMBER_ID, "race.png", "image/png", 1024));
		upload(fileId, TestImages.PNG, "image/png");

		complete(MEMBER_ID, fileId).andExpect(status().isBadRequest());

		assertThat(storedFileRepository.findById(fileId).orElseThrow().getStatus()).isEqualTo(FileStatus.DELETED);
	}

	@Test
	@DisplayName("다른 회원이 올린 파일은 완료할 수 없다")
	void rejectOtherMember() throws Exception {
		int fileId = issueAndUpload(TestImages.PNG, "image/png", TestImages.PNG);

		complete(OTHER_MEMBER_ID, fileId)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value("본인이 올린 파일만 처리할 수 있습니다."));
	}

	@Test
	@DisplayName("없는 파일이나 삭제된 파일은 404")
	void completeNotFound() throws Exception {
		complete(MEMBER_ID, 9999)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("존재하지 않는 파일입니다."));

		int deletedFileId = issueAndUpload(TestImages.TEXT, "image/png", TestImages.TEXT);
		complete(MEMBER_ID, deletedFileId).andExpect(status().isBadRequest());
		complete(MEMBER_ID, deletedFileId).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("파일 ID가 숫자가 아니면 400")
	void rejectNonNumericFileId() throws Exception {
		mockMvc.perform(post("/api/v1/file/files/abc/complete").header(MemberIdHeader.NAME, MEMBER_ID))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("요청 값의 형식이 올바르지 않습니다."));
	}

	private ResultActions issue(int memberId, String fileName, String contentType, long fileSize) throws Exception {
		return mockMvc.perform(post("/api/v1/file/files")
			.header(MemberIdHeader.NAME, memberId)
			.contentType(MediaType.APPLICATION_JSON)
			.content(uploadRequest(fileName, contentType, fileSize)));
	}

	private ResultActions complete(int memberId, int fileId) throws Exception {
		return mockMvc.perform(post("/api/v1/file/files/{fileId}/complete", fileId)
			.header(MemberIdHeader.NAME, memberId));
	}

	// 업로드 URL을 받고, 브라우저가 S3에 올린 것처럼 가짜 저장소에 넣는다
	private int issueAndUpload(byte[] declaredBody, String contentType, byte[] uploadedBody) throws Exception {
		int fileId = fileIdOf(issue(MEMBER_ID, "race-image", contentType, declaredBody.length));
		upload(fileId, uploadedBody, contentType);
		return fileId;
	}

	private void upload(int fileId, byte[] body, String contentType) {
		fakeFileStorage.upload(s3KeyOf(fileId), body, contentType);
	}

	private String s3KeyOf(int fileId) {
		return storedFileRepository.findById(fileId).orElseThrow().getS3Key();
	}

	private int fileIdOf(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.fileId");
	}

	private String uploadRequest(String fileName, String contentType, long fileSize) {
		return """
			{"fileName": "%s", "contentType": "%s", "fileSize": %d}
			""".formatted(fileName.replace("\\", "\\\\"), contentType, fileSize);
	}
}
