package com.bukang.file.in;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.bukang.file.support.FakeFileStorageConfig;

/**
 * @LoginMemberId 인자가 OpenAPI 명세(/v3/api-docs)에 X-Member-Id 헤더 칸으로 나오는지 확인한다.
 * 프론트(orval)가 이 헤더를 보고 요청 함수 인자에서 빼므로, 쿼리 칸(memberId)으로 바뀌면 프론트 함수가 달라진다.
 * 필수 헤더(업로드 URL 발급)와 선택 헤더(파일 조회)를 함께 확인한다
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(FakeFileStorageConfig.class)
class FileSwaggerMemberIdHeaderTest {
	private static final String FILES = "$.paths['/api/v1/file/files']";
	private static final String FILE = "$.paths['/api/v1/file/files/{fileId}']";
	private static final String MEMBER_ID_HEADER = "[?(@.name == 'X-Member-Id' && @.in == 'header')]";

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("필수 회원 ID는 필수 헤더 칸으로 나오고 memberId 쿼리 칸은 없다")
	void requiredHeader() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath(FILES + ".post.parameters" + MEMBER_ID_HEADER + ".required").value(hasItem(true)))
			.andExpect(jsonPath(FILES + ".post.parameters[?(@.name == 'memberId')]").isEmpty());
	}

	@Test
	@DisplayName("선택 회원 ID(required = false)는 선택 헤더 칸으로 나오고 memberId 쿼리 칸은 없다")
	void optionalHeader() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath(FILE + ".get.parameters" + MEMBER_ID_HEADER + ".required").value(hasItem(false)))
			.andExpect(jsonPath(FILE + ".get.parameters[?(@.name == 'memberId')]").isEmpty());
	}
}
