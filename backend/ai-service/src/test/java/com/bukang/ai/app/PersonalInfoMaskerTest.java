package com.bukang.ai.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PersonalInfoMaskerTest {
	@Test
	@DisplayName("휴대폰은 뒤 4자리, 이메일은 앞 2글자만 남기고 계좌처럼 긴 숫자는 가린다")
	void mask() {
		assertThat(PersonalInfoMasker.mask("연락처 010-1234-5678, 01098765432"))
			.isEqualTo("연락처 ***-****-5678, ***-****-5432");
		assertThat(PersonalInfoMasker.mask("메일 runner99@crew.run 로 주세요"))
			.isEqualTo("메일 ru***@*** 로 주세요");
		assertThat(PersonalInfoMasker.mask("계좌 110-123-456789 국민 1234567890"))
			.isEqualTo("계좌 (숫자 가림) 국민 (숫자 가림)");
		// 01로 시작하는 부분이 들어 있는 긴 숫자도 휴대폰으로 보지 않고 통째로 가린다
		assertThat(PersonalInfoMasker.mask("계좌 3020101234567890")).isEqualTo("계좌 (숫자 가림)");
		// 붙임표 바로 뒤에 붙여 써도 가린다
		assertThat(PersonalInfoMasker.mask("국민-110123456789")).isEqualTo("국민-(숫자 가림)");
	}

	@Test
	@DisplayName("주문번호, 날짜, 짧은 숫자는 그대로 둔다")
	void keep() {
		String text = "주문 OD-20261030-0217, od-20261030-0218, 11/3 10K 45,000원 2026-11-03";
		assertThat(PersonalInfoMasker.mask(text)).isEqualTo(text);
		assertThat(PersonalInfoMasker.mask(null)).isNull();
	}
}
