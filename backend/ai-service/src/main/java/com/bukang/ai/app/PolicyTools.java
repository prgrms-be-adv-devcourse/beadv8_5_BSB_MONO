package com.bukang.ai.app;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 판매자·관리자 화면 모두에 붙는 정책 안내 도구 (CRU-104). 문의 답변 초안과 같은 정책 목록(InquiryPolicy)을 쓴다
 * 조회 도구와 달리 주제를 인자로 받는다. 모델은 정해진 주제 이름 중에서만 고르고, 누구의 정보도 조회하지 않는다
 * 고객에게 「잠정」·「확정 전」 같은 말을 하지 않는다(다윗님 결정). 출처 이름의 「(잠정)」과 그런 문장은 빼고 넘긴다
 */
@Component
public class PolicyTools {
	// 주제 이름이 바뀌면 PolicyToolsTest가 이 설명에 모든 주제가 있는지 확인한다
	static final String TOPICS = "질문에 맞는 주제를 1~3개 고른다. "
		+ "REFUND_DEADLINE: 환불 기한·취소 수수료·일반 환불 정책, "
		+ "RACE_CANCEL: 대회가 취소됐을 때 참가자 환불, "
		+ "RACE_CANCEL_REQUEST: 판매자의 대회 취소 요청·승인·반려 절차, "
		+ "SCHEDULE_CHANGE: 대회 일정이 바뀌었을 때 환불, "
		+ "PARTY_CANCEL: 파티원 개별 취소, "
		+ "PARTICIPANT_CHANGE: 참가자 변경·양도·이름 수정, "
		+ "KIT_OPTION: 키트 사이즈·옵션 변경, "
		+ "KIT_RETURN: 키트 반품·교환·불량·오배송, "
		+ "WITHDRAWAL: 예치금 환급(현금·계좌로 돌려받기), "
		+ "WITHDRAWAL_DETAIL: 환급 실패·처리 중·알림, "
		+ "CHARGE: 예치금 충전 한도·충전 막힘, "
		+ "PERSONAL_INFO: 참가자 개인정보 보관·파기, "
		+ "RECOVERY: 1차 정산 뒤 판매자에게 돌려받는 돈(청구)·가상계좌 입금";
	static final String NO_POLICY = "맞는 정책이 없다. 지어내지 말고 「아직 크루런 AI로는 조회할 수 없어요. 해당 화면에서 확인해 주세요.」라고 답한다.";
	private static final Pattern TENTATIVE = Pattern.compile("잠정|확정|정해지지 않|회의");
	private static final Pattern SENTENCE_END = Pattern.compile("(?<=\\.) ");

	@Tool(description = "마지막 질문이 환불·취소·환급·충전·양도·키트 반품 같은 크루런의 규칙(정책)을 물을 때만 부른다"
		+ "(예: 「환불 정책 어떻게 돼?」, 「대회가 취소되면 환불은?」, 「키트 반품 돼?」). "
		+ "건수·금액 같은 운영 숫자 질문에는 부르지 않는다. "
		+ "크루런에는 포인트 적립·쿠폰·멤버십이 없다. 이런 질문처럼 주제 목록에 딱 맞는 주제가 없으면 부르지 않는다. "
		+ "policies의 rules에 있는 내용만 답에 쓰고, 답 첫 줄은 질문에 바로 답하는 한 문장으로 쓴다")
	public Object crewrunPolicy(@ToolParam(description = TOPICS) List<InquiryPolicy> topics, ToolContext toolContext) {
		AiToolTrace trace = AiToolTrace.of(toolContext);
		if (topics == null || topics.isEmpty()) {
			return Map.of("error", NO_POLICY);
		}
		List<Policy> policies = topics.stream().distinct()
			.map(policy -> new Policy(name(policy), rules(policy)))
			.toList();
		policies.forEach(policy -> trace.policy(policy.name()));
		return Map.of("policies", policies);
	}

	static String name(InquiryPolicy policy) {
		return policy.getSource().replace(" (잠정)", "");
	}

	// 「정해지지 않았다」 같은 문장은 빼고, 지금 서비스가 따르는 규칙만 남긴다
	static String rules(InquiryPolicy policy) {
		return policy.getRules().lines()
			.map(line -> Arrays.stream(SENTENCE_END.split(line))
				.filter(sentence -> !TENTATIVE.matcher(sentence).find())
				.collect(Collectors.joining(" ")))
			.filter(line -> !line.isBlank() && !line.equals("-"))
			.collect(Collectors.joining("\n"));
	}

	record Policy(String name, String rules) {
	}
}
