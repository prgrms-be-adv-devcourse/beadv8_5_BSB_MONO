package com.bukang.ai.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PolicyToolsTest {
	@Test
	@DisplayName("도구 설명에 모든 정책 주제가 있다 (정책을 더하면 설명도 고쳐야 모델이 고를 수 있다)")
	void topicsDescribeEveryPolicy() {
		assertThat(Arrays.stream(InquiryPolicy.values()).map(Enum::name))
			.allMatch(name -> PolicyTools.TOPICS.contains(name + ":"));
	}

	@Test
	@DisplayName("챗봇에 넘기는 정책 이름·규칙에는 「잠정」·「정해지지 않았다」 같은 말이 없다")
	void noTentativeWords() {
		for (InquiryPolicy policy : InquiryPolicy.values()) {
			assertThat(PolicyTools.name(policy) + PolicyTools.rules(policy))
				.as(policy.name())
				.doesNotContain("잠정", "확정", "정해지지 않", "회의");
		}
		assertThat(PolicyTools.name(InquiryPolicy.KIT_RETURN)).isEqualTo("정책 5장 3-1 키트 반품");
	}

	@Test
	@DisplayName("정해지지 않았다는 문장만 빼고 같은 줄의 규칙은 남긴다. 문의 초안이 쓰는 원문은 그대로다")
	void dropsOnlyTentativeSentence() {
		assertThat(PolicyTools.rules(InquiryPolicy.PERSONAL_INFO))
			.contains("- 참가자 정보는 대회가 끝난 뒤 일정 기간 보관한 다음 파기할 예정이다.")
			.doesNotContain("보관 기간과 파기 시점");
		assertThat(InquiryPolicy.PERSONAL_INFO.getRules()).contains("아직 정해지지 않았다");
		assertThat(InquiryPolicy.RACE_CANCEL.getSource()).endsWith("(잠정)");
	}
}
