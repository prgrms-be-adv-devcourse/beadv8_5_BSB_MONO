package com.bukang.order.in;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.exception.GlobalExceptionHandler;
import com.bukang.common.global.rsdata.RsData;
import com.bukang.common.shared.member.web.LoginMemberId;
import com.bukang.common.shared.member.web.LoginMemberIdArgumentResolver;
import com.bukang.common.shared.member.web.MemberIdHeader;

/**
 * 주문 서비스의 모든 API가 따르는 요청 헤더 규칙을 확인한다.
 * 헤더를 받는 테스트 전용 컨트롤러를 스프링 컨텍스트 없이(standalone) 붙여 확인한다.
 * 회원 ID 헤더는 common의 리졸버와 GlobalExceptionHandler가 처리하므로, 실제 서비스처럼 셋을 함께 붙인다.
 * 컨트롤러는 켜지 않는 프로파일에서만 빈이 되므로 다른 테스트의 컨텍스트에는 이 경로가 생기지 않는다.
 */
class OrderExceptionHandlerTest {
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new HeaderEchoController())
			.setCustomArgumentResolvers(new LoginMemberIdArgumentResolver())
			.setControllerAdvice(new OrderExceptionHandler(), new GlobalExceptionHandler())
			.build();
	}

	@Test
	@DisplayName("회원 ID 헤더가 있으면 공통 응답 형식으로 응답한다")
	void withMemberId() throws Exception {
		mockMvc.perform(get("/test/member").header("X-Member-Id", "7"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value(200))
			.andExpect(jsonPath("$.message").value("회원 ID를 받았습니다."))
			.andExpect(jsonPath("$.data").value(7));
	}

	@Test
	@DisplayName("회원 ID 헤더가 없으면 401과 공통 에러 형식으로 거절한다")
	void withoutMemberId() throws Exception {
		mockMvc.perform(get("/test/member"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.status").value(401))
			.andExpect(jsonPath("$.message").value("로그인이 필요합니다."))
			.andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"))
			.andExpect(jsonPath("$.data").doesNotExist());
	}

	@Test
	@DisplayName("회원 ID가 숫자가 아니면 400과 공통 에러 형식으로 거절한다")
	void invalidMemberId() throws Exception {
		mockMvc.perform(get("/test/member").header("X-Member-Id", "abc"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.message").value(MemberIdHeader.INVALID_MESSAGE))
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	@DisplayName("회원 ID가 아닌 필수 헤더가 없으면 401이 아니라 400으로 거절한다")
	void withoutOtherHeader() throws Exception {
		mockMvc.perform(get("/test/idempotent").header("X-Member-Id", "7"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.message").value("필수 요청 헤더(Idempotency-Key)가 없습니다."))
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	@DisplayName("필수 쿼리 파라미터가 없으면 400과 함께 빠진 파라미터 이름을 알려 준다")
	void withoutRequestParam() throws Exception {
		mockMvc.perform(get("/test/param").header("X-Member-Id", "7"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.message").value("필수 요청 값(keyword)이 없습니다."))
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	// standalone MockMvc는 @Controller가 붙은 클래스만 핸들러로 인식한다
	// 앱 전체 테스트(OrderExceptionHandlerIntegrationTest)에서만 쓰도록, 켜지 않는 프로파일에서만 빈이 되게 한다
	@RestController
	@Profile("order-exception-handler-test-only")
	static class HeaderEchoController {
		@GetMapping("/test/member")
		RsData<Long> member(@LoginMemberId long memberId) {
			return RsData.of(HttpStatus.OK, "회원 ID를 받았습니다.", memberId);
		}

		@GetMapping("/test/idempotent")
		RsData<String> idempotent(
			@LoginMemberId long memberId,
			@RequestHeader("Idempotency-Key") String idempotencyKey
		) {
			return RsData.of(HttpStatus.OK, "요청을 받았습니다.", idempotencyKey);
		}

		@GetMapping("/test/param")
		RsData<String> param(
			@LoginMemberId long memberId,
			@RequestParam("keyword") String keyword
		) {
			return RsData.of(HttpStatus.OK, "요청을 받았습니다.", keyword);
		}
	}
}
