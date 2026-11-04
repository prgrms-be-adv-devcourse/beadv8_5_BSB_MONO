package com.bukang.member.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdentityVerificationUseCaseTest {
	@Test
	@DisplayName("포트원이 숫자만 준 휴대폰 번호를 다른 회원과 같은 모양으로 바꾼다")
	void formatPhone() {
		assertThat(IdentityVerificationUseCase.formatPhone("01012345678")).isEqualTo("010-1234-5678");
		assertThat(IdentityVerificationUseCase.formatPhone("0111234567")).isEqualTo("011-123-4567");
		assertThat(IdentityVerificationUseCase.formatPhone(null)).isNull();
	}
}
