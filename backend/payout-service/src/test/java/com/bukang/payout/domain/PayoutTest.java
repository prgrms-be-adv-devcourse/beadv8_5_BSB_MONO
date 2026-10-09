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
	private static final BusinessDayCalendar CALENDAR = new BusinessDayCalendar();

	private Payout newPayout() {
		return new Payout(1, 10, YearMonth.of(2026, 9), LocalDateTime.of(2026, 9, 29, 3, 0), CALENDAR);
	}

	private Payout newPayout(YearMonth payoutMonth) {
		return new Payout(1, 10, payoutMonth, payoutMonth.atEndOfMonth().atTime(3, 0), CALENDAR);
	}

	@Test
	@DisplayName("지급 예정일은 정산 월의 다음 달 20일이다 (2026-09 → 10/20 화)")
	void scheduledPayDateIsNextMonth20th() {
		assertThat(newPayout(YearMonth.of(2026, 9)).getScheduledPayDate()).isEqualTo(LocalDate.of(2026, 10, 20));
	}

	@Test
	@DisplayName("다음 달 20일이 주말이면 다음 월요일에 지급한다 (2026-05 → 6/20 토 → 6/22, 2026-08 → 9/20 일 → 9/21)")
	void scheduledPayDateSkipsWeekend() {
		assertThat(newPayout(YearMonth.of(2026, 5)).getScheduledPayDate()).isEqualTo(LocalDate.of(2026, 6, 22));
		assertThat(newPayout(YearMonth.of(2026, 8)).getScheduledPayDate()).isEqualTo(LocalDate.of(2026, 9, 21));
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
