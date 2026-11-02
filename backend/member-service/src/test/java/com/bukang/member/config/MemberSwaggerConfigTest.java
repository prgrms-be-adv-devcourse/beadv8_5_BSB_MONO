package com.bukang.member.config;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

// 만들어진 OpenAPI 명세(/v3/api-docs)에 로그인 방식·헤더 칸과 설명·쿼리 칸이 맞게 나오는지 확인한다
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemberSwaggerConfigTest {
	private static final String ME = "$.paths['/api/v1/member/members/me']";
	private static final String EMAIL_AVAILABILITY = "$.paths['/api/v1/member/members/email-availability']";
	private static final String LOGIN = "$.paths['/api/v1/auth/login']";

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("Bearer(JWT) 보안 스키마가 있고, X-Member-Id를 받는 API에만 자물쇠와 헤더 설명이 붙는다")
	void bearerOnMemberIdOperations() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
			.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
			.andExpect(jsonPath(ME + ".get.security[0].bearerAuth").exists())
			.andExpect(jsonPath(ME + ".get.parameters[?(@.name == 'X-Member-Id')].description")
				.value(hasItem(containsString("게이트웨이"))))
			// @LoginMemberId 인자가 쿼리 칸(memberId)이 아니라 헤더 칸으로 나온다
			.andExpect(jsonPath(ME + ".get.parameters[?(@.name == 'X-Member-Id')].in").value(hasItem("header")))
			.andExpect(jsonPath(ME + ".get.parameters[?(@.name == 'memberId')]").isEmpty())
			.andExpect(jsonPath(LOGIN + ".post.security").doesNotExist());
	}

	@Test
	@DisplayName("이메일 확인 API는 쿼리 칸이 email 하나로 나온다 (@ParameterObject)")
	void emailAvailabilityFlatQuery() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath(EMAIL_AVAILABILITY + ".get.parameters.length()").value(1))
			.andExpect(jsonPath(EMAIL_AVAILABILITY + ".get.parameters[0].name").value("email"))
			.andExpect(jsonPath(EMAIL_AVAILABILITY + ".get.parameters[0].in").value("query"))
			.andExpect(jsonPath(EMAIL_AVAILABILITY + ".get.security").doesNotExist());
	}
}
