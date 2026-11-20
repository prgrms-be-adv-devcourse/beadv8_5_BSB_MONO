package com.bukang.ai.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.bukang.ai.out.ops.OpsPort;
import com.bukang.ai.support.AiTestBase;
import com.bukang.common.shared.member.web.MemberIdHeader;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// 도구가 화면별로 붙는지, 부른 도구대로 출처·목록이 응답에 붙는지 확인한다. 모델 대역이 도구를 대신 고른다
class ApiV1AiChatToolTest extends AiTestBase {
	private final ObjectMapper objectMapper = new ObjectMapper();
	private static final List<String> SELLER_TOOLS =
		List.of("sellerKitsWaitingForTracking", "sellerNextPayout", "sellerPayoutHoldReasons", "sellerMonthlySales",
			"crewrunPolicy");
	private static final List<String> ADMIN_TOOLS =
		List.of("adminWaitingInquiries", "adminPendingSellers", "crewrunPolicy");

	@Test
	@DisplayName("판매자 화면에는 판매자 도구만, 관리자 화면에는 관리자 도구만 모델에 보인다 (둘 다인 회원도). 정책 도구는 둘 다")
	void toolsByScreen() throws Exception {
		chat("seller", SELLER_AND_ADMIN, "질문").andExpect(status().isOk());
		assertThat(chatModel.lastToolNames()).containsExactlyInAnyOrderElementsOf(SELLER_TOOLS);

		chat("admin", SELLER_AND_ADMIN, "질문").andExpect(status().isOk());
		assertThat(chatModel.lastToolNames()).containsExactlyInAnyOrderElementsOf(ADMIN_TOOLS);
	}

	@Test
	@DisplayName("조회 도구는 인자를 받지 않는다 (모델이 회원 번호나 조회 대상을 정할 수 없다). 정책 도구만 정해진 주제 목록을 받는다")
	void toolsTakeNoArguments() throws Exception {
		for (String screen : List.of("seller", "admin")) {
			chat(screen, SELLER_AND_ADMIN, "질문").andExpect(status().isOk());
			List<String> names = chatModel.lastToolNames();
			List<String> schemas = chatModel.lastToolInputSchemas();
			for (int i = 0; i < names.size(); i++) {
				JsonNode properties = objectMapper.readTree(schemas.get(i)).path("properties");
				if (names.get(i).equals("crewrunPolicy")) {
					assertThat(properties.size()).isEqualTo(1);
					// 주제는 정책 목록(InquiryPolicy) 13개 이름 중에서만 고른다
					assertThat(properties.path("topics").path("items").path("enum").size()).isEqualTo(13);
				} else {
					assertThat(properties.isEmpty()).as(schemas.get(i)).isTrue();
				}
			}
		}
	}

	@Test
	@DisplayName("정책 질문에는 정책 도구가 고른 주제의 규칙만 모델에 가고, 답·출처에 「잠정」이 없다 (판매자·관리자 둘 다)")
	void policy() throws Exception {
		for (String screen : List.of("seller", "admin")) {
			chatModel.reset();
			chatModel.callTool("crewrunPolicy", "{\"topics\":[\"RACE_CANCEL\",\"KIT_RETURN\"]}");

			chat(screen, SELLER_AND_ADMIN, "대회가 취소되면 환불은 어떻게 돼요?")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.sources.length()").value(2))
				.andExpect(jsonPath("$.data.sources[0].screen").value("정책 5장 2차 2-3·2-5 대회 취소"))
				.andExpect(jsonPath("$.data.sources[0].asOf").isNotEmpty())
				.andExpect(jsonPath("$.data.sources[0].path").value((Object)null))
				.andExpect(jsonPath("$.data.sources[1].screen").value("정책 5장 3-1 키트 반품"))
				.andExpect(jsonPath("$.data.answer", not(containsString("잠정"))));

			assertThat(chatModel.toolResults()).singleElement().asString()
				.contains("관리자가 승인하면 참가자 공지와 전액 환불").contains("단순 변심 반품")
				.doesNotContain("잠정").doesNotContain("충전");
			assertThat(opsPort.calledMemberIds).isEmpty();
		}
	}

	@Test
	@DisplayName("모델이 목록에 없는 주제를 보내도 503이 아니고, 오류가 도구 결과로 모델에 돌아가며 정책 출처는 붙지 않는다")
	void policyUnknownTopic() throws Exception {
		chatModel.callTool("crewrunPolicy", "{\"topics\":[\"POINT\"]}");

		chat("seller", SELLER, "포인트 적립 정책은요?")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.sources").isEmpty());

		assertThat(chatModel.toolResults()).singleElement().asString().doesNotContain("policies");
	}

	@Test
	@DisplayName("정책 도구는 주제를 3개까지만 넘긴다 (다른 상황 규칙이 섞이지 않게)")
	void policyTopicLimit() throws Exception {
		chatModel.callTool("crewrunPolicy",
			"{\"topics\":[\"REFUND_DEADLINE\",\"RACE_CANCEL\",\"KIT_RETURN\",\"CHARGE\"]}");

		chat("seller", SELLER, "정책 다 알려줘")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.sources.length()").value(3));

		assertThat(chatModel.toolResults()).singleElement().asString().doesNotContain("충전");
	}

	@Test
	@DisplayName("정책 도구를 불렀어도 맞는 규칙이 없어 「조회할 수 없어요」로 답하면 정책 출처를 붙이지 않는다")
	void policyNotAnswered() throws Exception {
		chatModel.callTool("crewrunPolicy", "{\"topics\":[\"REFUND_DEADLINE\"]}");
		chatModel.answerWith("아직 크루런 AI로는 조회할 수 없어요. 해당 화면에서 확인해 주세요.");

		chat("seller", SELLER, "판매 수수료는 몇 퍼센트야?")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.sources").isEmpty());
	}

	@Test
	@DisplayName("정책 도구가 주제를 고르지 못하면 지어내지 말고 「조회할 수 없어요」로 답하라는 결과가 가고 출처는 붙지 않는다")
	void policyNoTopic() throws Exception {
		chatModel.callTool("crewrunPolicy", "{\"topics\":[]}");

		chat("seller", SELLER, "포인트 적립 정책은요?")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.sources").isEmpty());

		assertThat(chatModel.toolResults()).singleElement().asString().contains("아직 크루런 AI로는 조회할 수 없어요");
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
			.contains("\"sendableNowCount\":3").doesNotContain("김민지");
		// 대화 기록(Redis)에는 질문과 마지막 답만 남는다. 도구 결과와 목록은 남지 않는다
		assertThat(chatMemoryRepository.findByConversationId("seller-3")).hasSize(2)
			.noneMatch(message -> message.getText().contains("김민지") || message.getText().contains("ORD-1"));
	}

	@Test
	@DisplayName("모델이 판매자 화면에서 관리자 도구를 불러 달라고 해도 실행되지 않는다")
	void adminToolOnSellerScreen() throws Exception {
		chatModel.callTool("adminPendingSellers");

		chat("seller", SELLER_AND_ADMIN, "승인 대기 판매자는요?").andExpect(status().isServiceUnavailable());

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
	@DisplayName("한 질문에서 도구 호출이 상한을 넘으면 더 부르지 않고 503이고, 한도 초과 문구와 질문은 대화 기록에 남지 않는다")
	void toolCallLimit() throws Exception {
		chatModel.callToolForever("sellerNextPayout");

		chat("seller", SELLER, "다음 정산은 언제예요?")
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("AI_MODEL_UNAVAILABLE"));

		// 같은 도구는 2번까지(max-calls-per-tool-default), 모델 호출은 3번에서 멈춘다
		assertThat(opsPort.calledMemberIds).hasSize(2);
		assertThat(chatModel.prompts()).hasSize(3);
		assertThat(chatMemoryRepository.findByConversationId("seller-3")).isEmpty();
	}

	private ResultActions chat(String screen, long memberId, String message) throws Exception {
		return mockMvc.perform(post("/api/v1/ai/" + screen + "/chat")
			.header(MemberIdHeader.NAME, memberId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"message\":\"" + message + "\"}"));
	}
}
