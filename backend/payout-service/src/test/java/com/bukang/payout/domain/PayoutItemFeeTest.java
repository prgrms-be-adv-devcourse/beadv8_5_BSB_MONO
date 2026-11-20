package com.bukang.payout.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CRU-13: 수수료 계산을 PayoutItem.feeAmount() 한 곳에 두고 응답 DTO는 읽기만 한다.
 */
class PayoutItemFeeTest {
	private PayoutItem sale(long price, BigDecimal feeRate) {
		Payout payout = Payout.finalStage(1, 10, "대회", LocalDate.of(2026, 10, 17),
			LocalDateTime.of(2026, 10, 18, 3, 0), new BusinessDayCalendar());
		return PayoutItem.ofPayout(payout, PayoutRecordType.판매, 101L, 7L, price, feeRate, LocalDate.of(2026, 9, 26));
	}

	@Test
	@DisplayName("판매 줄의 수수료는 판매가 × 수수료율에서 원 미만을 버린 값이다")
	void feeAmountFloorsFraction() {
		// 50,001 × 5.5% = 2750.055 → 2750
		assertThat(sale(50_001L, new BigDecimal("0.0550")).feeAmount()).isEqualTo(2_750L);
		assertThat(sale(50_000L, new BigDecimal("0.0550")).feeAmount()).isEqualTo(2_750L);
	}
}
