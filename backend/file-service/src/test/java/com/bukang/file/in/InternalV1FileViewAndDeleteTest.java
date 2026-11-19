package com.bukang.file.in;

import static com.bukang.common.shared.file.domain.FileType.RECORD_CERT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.bukang.file.domain.FileFormat;
import com.bukang.file.domain.StoredFile;
import com.bukang.file.out.StoredFileRepository;
import com.bukang.file.support.FakeFileStorage;
import com.bukang.file.support.FakeFileStorageConfig;

// 기록증 원본을 member · ai-service가 받는 내부 API와, member가 30일 뒤 부르는 파일 삭제 내부 API (BUK-278)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(FakeFileStorageConfig.class)
class InternalV1FileViewAndDeleteTest {
	private static final long OWNER_ID = 1;

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
	@DisplayName("보기 주소 발급은 올린 회원이 아니어도 기록증의 presigned GET URL을 준다")
	void viewUrl() throws Exception {
		StoredFile cert = linkedCert();

		mockMvc.perform(get("/internal/v1/file/files/{fileId}/view-url", cert.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("파일 보기 주소를 발급했습니다."))
			.andExpect(jsonPath("$.data.id").value(cert.getId()))
			.andExpect(jsonPath("$.data.url").value("http://fake-s3/bukang-file/" + cert.getS3Key()
				+ "?X-Amz-Signature=fake-get"));
		mockMvc.perform(get("/internal/v1/file/files/{fileId}/view-url", 9999))
			.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("올린 사람 조회는 파일을 올린 회원 ID와 용도 · 상태를 주고, 없거나 삭제된 파일이면 404로 답한다 (BUK-274)")
	void uploader() throws Exception {
		StoredFile cert = linkedCert();

		mockMvc.perform(get("/internal/v1/file/files/{fileId}/uploader", cert.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.memberId").value(OWNER_ID))
			.andExpect(jsonPath("$.data.fileType").value("RECORD_CERT"))
			.andExpect(jsonPath("$.data.status").value("ACTIVE"));
		mockMvc.perform(get("/internal/v1/file/files/{fileId}/uploader", 9999))
			.andExpect(status().isNotFound());
		cert.delete();
		storedFileRepository.save(cert);
		mockMvc.perform(get("/internal/v1/file/files/{fileId}/uploader", cert.getId()))
			.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("파일 삭제는 행과 S3 객체를 함께 지우고, 이미 없는 파일도 성공으로 답한다")
	void deleteFile() throws Exception {
		StoredFile cert = linkedCert();
		fakeFileStorage.upload(cert.getS3Key(), new byte[] {1}, "image/png");

		mockMvc.perform(delete("/internal/v1/file/files/{fileId}", cert.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("파일을 지웠습니다."));

		assertThat(storedFileRepository.findById(cert.getId())).isEmpty();
		assertThat(fakeFileStorage.exists(cert.getS3Key())).isFalse();
		mockMvc.perform(get("/internal/v1/file/files/{fileId}/view-url", cert.getId()))
			.andExpect(status().isNotFound());
		mockMvc.perform(delete("/internal/v1/file/files/{fileId}", cert.getId()))
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("S3 객체를 지우지 못하면 503으로 답하고 행을 남겨, S3가 돌아온 뒤 다시 부르면 둘 다 지운다")
	void deleteFailsThenRecovers() throws Exception {
		StoredFile partial = linkedCert();
		StoredFile requestFails = linkedCert();
		fakeFileStorage.upload(partial.getS3Key(), new byte[] {1}, "image/png");
		fakeFileStorage.upload(requestFails.getS3Key(), new byte[] {1}, "image/png");

		fakeFileStorage.failOnDelete(partial.getS3Key());
		mockMvc.perform(delete("/internal/v1/file/files/{fileId}", partial.getId()))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.message").value("파일을 지우지 못했습니다. 잠시 뒤 다시 시도해주세요."));
		fakeFileStorage.recoverDelete();
		fakeFileStorage.failDeleteRequest();
		mockMvc.perform(delete("/internal/v1/file/files/{fileId}", requestFails.getId()))
			.andExpect(status().isServiceUnavailable());

		assertThat(storedFileRepository.findById(partial.getId())).isPresent();
		assertThat(storedFileRepository.findById(requestFails.getId())).isPresent();

		fakeFileStorage.recoverDelete();
		mockMvc.perform(delete("/internal/v1/file/files/{fileId}", partial.getId()))
			.andExpect(status().isOk());
		mockMvc.perform(delete("/internal/v1/file/files/{fileId}", requestFails.getId()))
			.andExpect(status().isOk());

		assertThat(storedFileRepository.findById(partial.getId())).isEmpty();
		assertThat(storedFileRepository.findById(requestFails.getId())).isEmpty();
		assertThat(fakeFileStorage.exists(partial.getS3Key())).isFalse();
		assertThat(fakeFileStorage.exists(requestFails.getS3Key())).isFalse();
	}

	private StoredFile linkedCert() {
		StoredFile file = StoredFile.pending(
			"uploads/" + UUID.randomUUID() + ".png", "cert.png", RECORD_CERT, FileFormat.PNG, 70, OWNER_ID);
		file.confirmUpload(file.getS3Key().replace("uploads/", "files/"));
		file.linkTo("RecordCert", 1, 0);
		return storedFileRepository.save(file);
	}
}
