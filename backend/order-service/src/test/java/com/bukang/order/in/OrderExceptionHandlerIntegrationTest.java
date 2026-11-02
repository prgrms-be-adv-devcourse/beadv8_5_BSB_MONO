package com.bukang.order.in;

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

import com.bukang.common.shared.member.web.MemberIdHeader;

/**
 * 앱 전체를 띄워 common의 GlobalExceptionHandler와 OrderExceptionHandler가 함께 등록된 상태에서도
 * 헤더·파라미터 누락이 정해진 응답(401/400)으로 나가는지 확인한다.
 * OrderExceptionHandler가 가장 앞 순서여도 회원 ID 헤더 오류는 common이 응답하는지(가로채지 않는지)도 확인한다
 * 테스트용 컨트롤러(OrderExceptionHandlerTest.HeaderEchoController)는 테스트 클래스 안의 클래스라 컴포넌트 스캔에서 빠지므로
 * @Import로 넣고, 그 컨트롤러의 프로파일을 이 테스트에서만 켠다
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "order-exception-handler-test-only"})
@Import(OrderExceptionHandlerTest.HeaderEchoController.class)
class OrderExceptionHandlerIntegrationTest {
	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("앱 전체에서도 회원 ID 헤더가 없으면 401, 숫자가 아니면 400으로 거절한다")
	void withoutMemberId() throws Exception {
		mockMvc.perform(get("/test/member"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("로그인이 필요합니다."))
			.andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
		mockMvc.perform(get("/test/member").header("X-Member-Id", "abc"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(MemberIdHeader.INVALID_MESSAGE))
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	@DisplayName("앱 전체에서도 다른 필수 헤더·파라미터가 없으면 400으로 이름을 알려 준다")
	void withoutOtherHeaderOrParam() throws Exception {
		mockMvc.perform(get("/test/idempotent").header("X-Member-Id", "7"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("필수 요청 헤더(Idempotency-Key)가 없습니다."))
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		mockMvc.perform(get("/test/param").header("X-Member-Id", "7"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("필수 요청 값(keyword)이 없습니다."))
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}
}
