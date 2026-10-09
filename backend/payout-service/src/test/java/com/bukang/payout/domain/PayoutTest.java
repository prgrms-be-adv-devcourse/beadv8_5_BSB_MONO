package com.bukang.payout.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PayoutTest {
	private static final BigDecimal FEE_RATE = new BigDecimal("0.0550");
	private static final LocalDate TARGET_DATE = LocalDate.of(2026, 9, 29);

	private Payout newPayout() {
		return new Payout(1, 10, YearMonth.of(2026, 9), LocalDateTime.of(2026, 9, 29, 3, 0));
	}

	@Test
	@DisplayName("5만 원 참가권 2장이면 판매액 100,000, 수수료 5,500, 지급액 94,500이다")
	void amountsForTwoTickets() {
		Payout payout = newPayout();

		payout.addItem(100, 50_000L, FEE_RATE, TARGET_DATE);
		payout.addItem(101, 50_000L, FEE_RATE, TARGET_DATE);

		assertThat(payout.getSalesAmount()).isEqualTo(100_000L);
		assertThat(payout.getRefundAmount()).isZero();
		assertThat(payout.getFeeAmount()).isEqualTo(5_500L);
		assertThat(payout.getPayoutAmount()).isEqualTo(94_500L);
	}

	@Test
	@DisplayName("수수료는 장마다 원 미만을 버리고 합한다 (10,010원 × 10장: 합계로 계산한 5,505가 아니라 5,500)")
	void feeIsFlooredPerTicket() {
		Payout payout = newPayout();

		for (int orderItemId = 1; orderItemId <= 10; orderItemId++) {
			payout.addItem(orderItemId, 10_010L, FEE_RATE, TARGET_DATE);
		}

		assertThat(payout.getFeeAmount()).isEqualTo(5_500L);
		assertThat(payout.getPayoutAmount()).isEqualTo(100_100L - 5_500L);
	}
}
