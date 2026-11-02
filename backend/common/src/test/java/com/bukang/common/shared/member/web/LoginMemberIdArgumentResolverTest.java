package com.bukang.common.shared.member.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.exception.GlobalExceptionHandler;
import com.bukang.common.global.rsdata.RsData;

/**
 * @LoginMemberId 인자가 헤더 값에 따라 회원 ID를 넣거나, 공통 오류 형식(401/400)으로 거절하는지 확인한다.
 * 실제 서비스처럼 리졸버와 GlobalExceptionHandler를 함께 붙인다
 */
class LoginMemberIdArgumentResolverTest {
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new MemberEchoController())
			.setCustomArgumentResolvers(new LoginMemberIdArgumentResolver())
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
	}

	@Test
	@DisplayName("헤더가 숫자면 그 값을 회원 ID로 넘긴다")
	void validHeader() throws Exception {
		mockMvc.perform(get("/test/member").header(MemberIdHeader.NAME, "5"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").value(5L));
	}

	@Test
	@DisplayName("int 범위를 넘는 값도 long 인자에는 그대로 넘긴다")
	void beyondIntRange() throws Exception {
		mockMvc.perform(get("/test/member").header(MemberIdHeader.NAME, "3000000000"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").value(3_000_000_000L));
	}

	@Test
	@DisplayName("헤더가 없으면 401과 LOGIN_REQUIRED로 거절한다")
	void missingHeader() throws Exception {
		mockMvc.perform(get("/test/member"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.status").value(401))
			.andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"))
			.andExpect(jsonPath("$.message").value("로그인이 필요합니다."))
			.andExpect(jsonPath("$.data").doesNotExist());
	}

	@Test
	@DisplayName("헤더가 숫자가 아니면 400과 INVALID_REQUEST로 거절한다")
	void notNumber() throws Exception {
		mockMvc.perform(get("/test/member").header(MemberIdHeader.NAME, "abc"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
			.andExpect(jsonPath("$.message").value(MemberIdHeader.INVALID_MESSAGE));
	}

	@Test
	@DisplayName("헤더가 0이면 400과 INVALID_REQUEST로 거절한다")
	void zero() throws Exception {
		mockMvc.perform(get("/test/member").header(MemberIdHeader.NAME, "0"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
			.andExpect(jsonPath("$.message").value(MemberIdHeader.INVALID_MESSAGE));
	}

	@Test
	@DisplayName("required = false면 헤더가 없을 때 null을 넘긴다")
	void optionalMissingHeader() throws Exception {
		mockMvc.perform(get("/test/optional"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").doesNotExist());
	}

	@Test
	@DisplayName("required = false여도 형식이 틀린 헤더는 400으로 거절한다")
	void optionalInvalidHeader() throws Exception {
		mockMvc.perform(get("/test/optional").header(MemberIdHeader.NAME, "-1"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@RestController
	static class MemberEchoController {
		@GetMapping("/test/member")
		RsData<Long> member(@LoginMemberId long memberId) {
			return RsData.of(HttpStatus.OK, "회원 ID를 받았습니다.", memberId);
		}

		@GetMapping("/test/optional")
		RsData<Long> optional(@LoginMemberId(required = false) Long memberId) {
			return RsData.of(HttpStatus.OK, "회원 ID를 받았습니다.", memberId);
		}
	}
}
