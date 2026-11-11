package com.bukang.ai.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallLimitExceededException;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.bukang.ai.out.ops.OpsPort;
import com.bukang.ai.support.AiTestBase;
import com.bukang.common.shared.member.web.MemberIdHeader;

// 도구가 화면별로 붙는지, 부른 도구대로 출처·목록이 응답에 붙는지 확인한다. 모델 대역이 도구를 대신 고른다
class ApiV1AiChatToolTest extends AiTestBase {
	private static final List<String> SELLER_TOOLS =
		List.of("sellerKitsWaitingForTracking", "sellerNextPayout", "sellerPayoutHoldReasons");
	private static final List<String> ADMIN_TOOLS = List.of("adminWaitingInquiries", "adminPendingSellers");

	@Autowired
	private ToolCallingManager toolCallingManager;

	@Test
	@DisplayName("판매자 화면에는 판매자 도구만, 관리자 화면에는 관리자 도구만 모델에 보인다 (둘 다인 회원도)")
	void toolsByScreen() throws Exception {
		chat("seller", SELLER_AND_ADMIN, "질문").andExpect(status().isOk());
		assertThat(chatModel.lastToolNames()).containsExactlyInAnyOrderElementsOf(SELLER_TOOLS);

		chat("admin", SELLER_AND_ADMIN, "질문").andExpect(status().isOk());
		assertThat(chatModel.lastToolNames()).containsExactlyInAnyOrderElementsOf(ADMIN_TOOLS);
	}

	@Test
	@DisplayName("도구를 부르지 않은 답은 출처가 비어 있고 목록이 없다")
	void noTool() throws Exception {
		chat("seller", SELLER, "안녕하세요")
			.andExpect(jsonPath("$.data.sources").isEmpty())
			.andExpect(jsonPath("$.data.list").value((Object)null));
	}

	@Test
	@DisplayName("부른 도구의 출처가 붙고, 도구는 로그인한 회원 번호로 조회한다")
	void source() throws Exception {
		opsPort.payoutSummary = new OpsPort.PayoutSummary(LocalDate.of(2026, 11, 20), 1250000, 1);
		chatModel.callTool("sellerNextPayout");

		chat("seller", OTHER_SELLER, "다음 정산은 언제예요?")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.sources.length()").value(1))
			.andExpect(jsonPath("$.data.sources[0].screen").value("정산 · 정산 요약"))
			.andExpect(jsonPath("$.data.sources[0].asOf").isNotEmpty())
			.andExpect(jsonPath("$.data.sources[0].buttonLabel").value("정산으로 가기"))
			.andExpect(jsonPath("$.data.sources[0].path").value("/seller/settlements"))
			.andExpect(jsonPath("$.data.list").value((Object)null));

		assertThat(opsPort.calledMemberIds).containsOnly(OTHER_SELLER);
		assertThat(chatModel.toolResults()).singleElement().asString()
			.contains("\"nextPayDate\":\"2026-11-20\"").contains("\"scheduledPayoutAmount\":1250000");
	}

	@Test
	@DisplayName("키트 목록은 응답 data에만 담기고, 모델에 간 도구 결과에는 구매자 이름이 없다")
	void kitList() throws Exception {
		opsPort.kitSummary = new OpsPort.KitSummary(3, 0);
		opsPort.kitPage = new OpsPort.KitPage(List.of(new OpsPort.Kit("ORD-1", "김민지", "10km")), 3);
		chatModel.callTool("sellerKitsWaitingForTracking");

		chat("seller", SELLER, "송장 입력 대기 키트는 몇 개예요?")
			.andExpect(jsonPath("$.data.sources[0].path").value("/seller/kits"))
			.andExpect(jsonPath("$.data.list.rows[0].orderNo").value("ORD-1"))
			.andExpect(jsonPath("$.data.list.rows[0].name").value("김민지"))
			.andExpect(jsonPath("$.data.list.rows[0].detail").value("10km"))
			.andExpect(jsonPath("$.data.list.moreCount").value(2));

		assertThat(chatModel.toolResults()).singleElement().asString()
			.contains("\"waitingCount\":3").doesNotContain("김민지");
	}

	@Test
	@DisplayName("관리자 도구는 판매자 화면에서 부를 수 없다")
	void adminToolOnSellerScreen() throws Exception {
		chatModel.callTool("adminPendingSellers");

		chat("seller", SELLER_AND_ADMIN, "승인 대기 판매자는요?").andExpect(jsonPath("$.data.sources").isEmpty());

		assertThat(chatModel.toolResults()).isEmpty();
		assertThat(opsPort.calledMemberIds).isEmpty();
	}

	@Test
	@DisplayName("도구가 실패하면 답은 그대로 나가고, 출처는 조회 시각 없이 붙는다 (버튼 없는 화면은 경로도 없다)")
	void toolFailure() throws Exception {
		opsPort.unavailable = true;
		chatModel.callTool("adminWaitingInquiries");

		chat("admin", ADMIN, "답변 대기 문의는요?")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.sources[0].screen").value("문의 관리 · 답변 대기"))
			.andExpect(jsonPath("$.data.sources[0].asOf").value((Object)null))
			.andExpect(jsonPath("$.data.sources[0].path").value((Object)null));

		assertThat(chatModel.toolResults()).singleElement().asString().contains("숫자를 말하지 말고");
	}

	@Test
	@DisplayName("한 질문에서 도구를 4번 넘게 부르면 막는다 (spring.ai.tools.limits)")
	void toolCallLimit() throws Exception {
		chat("seller", SELLER, "질문").andExpect(status().isOk());
		Prompt prompt = new Prompt(List.<Message>of(new UserMessage("질문")), chatModel.lastPrompt().getOptions());
		List<AssistantMessage.ToolCall> calls = IntStream.range(0, 5)
			.mapToObj(i -> new AssistantMessage.ToolCall("call-" + i, "function", SELLER_TOOLS.get(i % 3), "{}"))
			.toList();
		ChatResponse response = new ChatResponse(List.of(
			new Generation(AssistantMessage.builder().content("").toolCalls(calls).build())));

		assertThatThrownBy(() -> toolCallingManager.executeToolCalls(prompt, response))
			.isInstanceOf(ToolCallLimitExceededException.class);
	}

	private ResultActions chat(String screen, long memberId, String message) throws Exception {
		return mockMvc.perform(post("/api/v1/ai/" + screen + "/chat")
			.header(MemberIdHeader.NAME, memberId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"message\":\"" + message + "\"}"));
	}
}
