package com.bukang.ai.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.bukang.ai.out.ops.OpsPort;
import com.bukang.ai.support.AiTestBase;
import com.bukang.common.shared.member.web.MemberIdHeader;

// 브리핑 API의 응답 모양(프론트 AxBriefing 타입), 역할 확인, 모델을 부르지 않는지 확인한다
class ApiV1AiBriefingTest extends AiTestBase {
	@Test
	@DisplayName("판매자 브리핑은 프론트 계약 모양으로 오고, 모델을 부르지 않는다")
	void seller() throws Exception {
		opsPort.kitSummary = new OpsPort.KitSummary(4, 0);
		opsPort.payoutSummary = new OpsPort.PayoutSummary(LocalDate.of(2026, 11, 20), 1250000, 0);

		briefing("seller", SELLER)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("브리핑을 만들었습니다."))
			.andExpect(jsonPath("$.data.headline").value("송장 입력 대기 키트가 4건 있어요."))
			.andExpect(jsonPath("$.data.basis").isNotEmpty())
			.andExpect(jsonPath("$.data.todos[0].title").value("송장 입력 대기 4건"))
			.andExpect(jsonPath("$.data.todos[0].linkLabel").value("키트 배송 관리"))
			.andExpect(jsonPath("$.data.todos[0].href").value("/seller/kits"))
			.andExpect(jsonPath("$.data.metrics[1].value").value("₩1,250,000"))
			// noteTone이 없으면 칸을 빼서 프론트 타입(noteTone?)과 맞춘다
			.andExpect(jsonPath("$.data.metrics[0].noteTone").doesNotExist());

		assertThat(chatModel.prompts()).isEmpty();
		assertThat(opsPort.calledMemberIds).containsOnly(SELLER);
	}

	@Test
	@DisplayName("관리자 브리핑은 조회가 실패해도 200이다")
	void adminFailed() throws Exception {
		opsPort.unavailable = true;

		briefing("admin", ADMIN)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.metrics[0].value").value("-"));
	}

	@Test
	@DisplayName("그 화면의 역할이 없으면 403, 없는 화면은 404다")
	void forbidden() throws Exception {
		briefing("admin", SELLER).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("AI_FORBIDDEN"));
		briefing("seller", MEMBER).andExpect(status().isForbidden());
		briefing("buyer", SELLER).andExpect(status().isNotFound());
		assertThat(opsPort.calledMemberIds).isEmpty();
	}

	private ResultActions briefing(String screen, long memberId) throws Exception {
		return mockMvc.perform(get("/api/v1/ai/{screen}/briefing", screen)
			.header(MemberIdHeader.NAME, memberId));
	}
}
