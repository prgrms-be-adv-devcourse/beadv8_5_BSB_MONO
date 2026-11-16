package com.bukang.ai.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.bukang.ai.domain.AiScreen;
import com.bukang.ai.out.ops.OpsPort;
import com.bukang.ai.support.AiTestBase;
import com.bukang.common.shared.member.web.MemberIdHeader;

// 문의 답변 초안: 누가 어느 문의로 만들 수 있는지, 모델에 무엇을 보내는지, 실패하면 어떻게 답하는지 확인한다
class ApiV1AiInquiryDraftTest extends AiTestBase {
	private static final String DRAFT_JSON = """
		{"draft": "안녕하세요, 크루런이에요. 환불 일정은 [확인 필요: 환불 일정] 확인 후 안내해 드릴게요.",
		"missing": [{"title": "환불 일정을 넣지 못했어요", "reason": "대회 취소 환불 일정이 자료에 없어요."}]}
		""";
	private static final String GLOSSARY = "용어 「취소」·「환불」·「환급」 뜻";
	// 신청·결제 문의(테스트 문의 종류)에 붙는 정책 출처
	private static final String[] POLICIES = {"정책 5장 2차 1 예치금 환급", "정책 4장 2차 2 예치금 충전",
		"정책 5장 2차 2-1 파티원 취소", "정책 5장 2차 2-3·2-5 대회 취소"};
	private static final OpsPort.InquiryOrder ORDER = new OpsPort.InquiryOrder("OD-20261030-0217", 45000L, "PAID",
		List.of(new OpsPort.InquiryOrderItem("춘천 호수 하프마라톤", "10K", "티셔츠 M", "PREPARING")));

	@Test
	@DisplayName("관리자: 관리자용 상세로 문의를 읽어 초안을 만들고, 참고한 정보와 조회 시각은 코드가 붙인다")
	void adminDraft() throws Exception {
		opsPort.inquiry = inquiry("WAITING", "대회가 취소되면 환불은 언제 되나요? 연락은 010-1234-5678로 주세요.", ORDER);
		chatModel.answerWith(DRAFT_JSON);

		draft("admin", ADMIN, 12)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.draft").value(
				"안녕하세요, 크루런이에요. 환불 일정은 [확인 필요: 환불 일정] 확인 후 안내해 드릴게요."))
			.andExpect(jsonPath("$.data.missing[0].title").value("환불 일정을 넣지 못했어요"))
			.andExpect(jsonPath("$.data.missing[0].reason").value("대회 취소 환불 일정이 자료에 없어요."))
			.andExpect(jsonPath("$.data.references").value(contains(
				"주문 OD-20261030-0217", "결제 금액(예치금)", "키트 배송 상태", POLICIES[0], POLICIES[1], POLICIES[2],
				POLICIES[3], GLOSSARY)))
			.andExpect(jsonPath("$.data.asOf").isNotEmpty());

		assertThat(opsPort.calledMemberIds).containsExactly(ADMIN);
		assertThat(opsPort.inquiryScreen).isEqualTo(AiScreen.ADMIN);
		assertThat(opsPort.inquiryId).isEqualTo(12L);
		String sent = chatModel.lastPrompt().getContents();
		assertThat(sent).contains("관리자 센터", "OD-20261030-0217", "45000원", "춘천 호수 하프마라톤 10K", "***-****-5678")
			.doesNotContain("010-1234-5678");
	}

	@Test
	@DisplayName("초안은 대화 기록(Redis)에 남지 않는다")
	void notInChatMemory() throws Exception {
		opsPort.inquiry = inquiry("WAITING", "본문", ORDER);
		chatModel.answerWith(DRAFT_JSON);

		draft("admin", ADMIN, 12).andExpect(status().isOk());

		assertThat(chatMemoryRepository.findConversationIds()).isEmpty();
	}

	@Test
	@DisplayName("판매자: 판매자용 상세로 읽는다. 결제 금액이 없으면 참고한 정보에도 없다")
	void sellerDraft() throws Exception {
		OpsPort.InquiryOrder noPayment = new OpsPort.InquiryOrder("OD-1", null, "PAID",
			List.of(new OpsPort.InquiryOrderItem("한강 나이트런", "5K", null, null)));
		opsPort.inquiry = inquiry("WAITING", "주차는 어디에 하나요?", noPayment);
		chatModel.answerWith("{\"draft\": \"안녕하세요, 크루런이에요.\"}");

		draft("seller", SELLER, 12)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.missing").isEmpty())
			.andExpect(jsonPath("$.data.references").value(contains(
				"주문 OD-1", POLICIES[0], POLICIES[1], POLICIES[2], POLICIES[3], GLOSSARY)));

		assertThat(opsPort.inquiryScreen).isEqualTo(AiScreen.SELLER);
		assertThat(chatModel.lastPrompt().getContents()).contains("판매자 센터").doesNotContain("결제 금액:");
	}

	@Test
	@DisplayName("주문이 없는 문의는 주문 없음으로 보내고, 참고한 정보는 용어뿐이다")
	void noOrder() throws Exception {
		opsPort.inquiry = inquiry("WAITING", "정산 지급일이 언제인가요?", null);
		chatModel.answerWith(DRAFT_JSON);

		draft("admin", ADMIN, 12)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.references").value(contains(
				POLICIES[0], POLICIES[1], POLICIES[2], POLICIES[3], GLOSSARY)));
		assertThat(chatModel.lastPrompt().getContents()).contains("[주문]\n없음");
	}

	@Test
	@DisplayName("모델이 비운 missing 항목은 응답에서 뺀다")
	void blankMissing() throws Exception {
		opsPort.inquiry = inquiry("WAITING", "본문", ORDER);
		chatModel.answerWith("{\"draft\": \"초안\", "
			+ "\"missing\": [null, {\"title\": \" \"}, {\"title\": \"날짜를 넣지 못했어요\"}]}");

		draft("admin", ADMIN, 12)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.missing.length()").value(1))
			.andExpect(jsonPath("$.data.missing[0].title").value("날짜를 넣지 못했어요"));
	}

	@Test
	@DisplayName("그 화면의 역할이 없으면 403이고 문의를 읽지 않는다")
	void forbidden() throws Exception {
		draft("admin", SELLER, 12).andExpect(status().isForbidden());
		draft("seller", MEMBER, 12).andExpect(status().isForbidden());

		assertThat(opsPort.calledMemberIds).isEmpty();
	}

	@Test
	@DisplayName("문의 서비스가 없는 문의로 답하면 404이고 모델을 부르지 않는다")
	void notFound() throws Exception {
		draft("seller", SELLER, 12)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("AI_INQUIRY_NOT_FOUND"));
		assertThat(chatModel.prompts()).isEmpty();
	}

	@Test
	@DisplayName("이미 답변한 문의는 409이고 모델을 부르지 않는다")
	void alreadyAnswered() throws Exception {
		opsPort.inquiry = inquiry("ANSWERED", "본문", ORDER);

		draft("admin", ADMIN, 12)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("AI_INQUIRY_ALREADY_ANSWERED"));
		assertThat(chatModel.prompts()).isEmpty();
	}

	@Test
	@DisplayName("문의 서비스에 연결하지 못하면 503")
	void inquiryUnavailable() throws Exception {
		opsPort.unavailable = true;

		draft("admin", ADMIN, 12)
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("AI_EXTERNAL_SERVICE_UNAVAILABLE"))
			.andExpect(jsonPath("$.message").value("문의 서비스에 연결하지 못했습니다. 잠시 뒤 다시 시도해주세요."));
	}

	@Test
	@DisplayName("모델이 실패하거나, 읽을 수 없는 답·빈 초안·2,000자 넘는 초안을 주면 503")
	void modelFailure() throws Exception {
		opsPort.inquiry = inquiry("WAITING", "본문", ORDER);
		List<String> badAnswers = List.of("초안입니다", "{\"draft\": \" \"}",
			"{\"draft\": \"" + "가".repeat(2001) + "\"}");
		for (String answer : badAnswers) {
			chatModel.answerWith(answer);
			draft("admin", ADMIN, 12)
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("AI_MODEL_UNAVAILABLE"));
		}

		chatModel.failWith(new IllegalStateException("모델 꺼짐"));
		draft("admin", ADMIN, 12)
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("AI_MODEL_UNAVAILABLE"));
	}

	@Test
	@DisplayName("화면 종류나 문의 ID가 맞지 않는 주소는 404")
	void wrongPath() throws Exception {
		draft("member", ADMIN, 12).andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/ai/admin/inquiries/abc/draft").header(MemberIdHeader.NAME, ADMIN))
			.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/ai/admin/inquiries/99999999999999999999/draft")
				.header(MemberIdHeader.NAME, ADMIN))
			.andExpect(status().isNotFound());
	}

	private static OpsPort.Inquiry inquiry(String status, String body, OpsPort.InquiryOrder order) {
		return new OpsPort.Inquiry("APPLY_PAYMENT", "환불 문의", body, status,
			LocalDateTime.of(2026, 11, 3, 9, 12), order);
	}

	private ResultActions draft(String screen, long memberId, long inquiryId) throws Exception {
		return mockMvc.perform(post("/api/v1/ai/{screen}/inquiries/{id}/draft", screen, inquiryId)
			.header(MemberIdHeader.NAME, memberId));
	}
}
