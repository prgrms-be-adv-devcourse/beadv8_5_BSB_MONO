package com.bukang.product.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 크루 결제율 미달 기준(25% 이하)의 경계를 확인한다 (BUK-176)
 */
class CrewLowPaymentPolicyTest {
	@Test
	@DisplayName("파티원 4명 중 1명만 결제하면(25%) 결제율 미달이다")
	void lowAtQuarter() {
		// given
		long memberCount = 4;
		long paidCount = 1;

		// when
		boolean low = CrewLowPaymentPolicy.isLow(paidCount, memberCount);

		// then
		assertThat(low).isTrue();
	}

	@Test
	@DisplayName("파티원 4명 중 2명이 결제하면(50%) 결제율 미달이 아니다")
	void notLowAtHalf() {
		// given
		long memberCount = 4;
		long paidCount = 2;

		// when
		boolean low = CrewLowPaymentPolicy.isLow(paidCount, memberCount);

		// then
		assertThat(low).isFalse();
	}
}
