package com.bukang.cash.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.cash.domain.charge.CashChargePolicy;

// 기대값은 정책 5장 2차 1 「PG 결제취소와 기한」의 예시에서 가져온다
class CashChargePolicyTest {
	@Test
	@DisplayName("2026-11-05에 승인된 충전은 2027-11-04까지 카드로 취소할 수 있다")
	void lastCancelableDateIsOneYearMinusOneDay() {
		LocalDate lastDate = CashChargePolicy.lastCancelableDate(LocalDateTime.of(2026, 11, 5, 14, 0));

		assertThat(lastDate).isEqualTo(LocalDate.of(2027, 11, 4));
	}

	@Test
	@DisplayName("2024-02-29에 승인된 충전은 다음 해에 2월 29일이 없어 2025-02-27까지 카드로 취소할 수 있다")
	void lastCancelableDateOnLeapDay() {
		LocalDate lastDate = CashChargePolicy.lastCancelableDate(LocalDateTime.of(2024, 2, 29, 9, 0));

		assertThat(lastDate).isEqualTo(LocalDate.of(2025, 2, 27));
	}

	@Test
	@DisplayName("마지막 날(2027-10-09) 24:00 전까지는 카드로 취소할 수 있고, 다음 날 00:00부터는 기한이 지난다")
	void cancelableUntilMidnightOfLastDate() {
		LocalDateTime approvedAt = LocalDateTime.of(2026, 10, 10, 18, 0);

		assertThat(CashChargePolicy.isCancelable(approvedAt, LocalDateTime.of(2027, 10, 9, 23, 59, 59))).isTrue();
		assertThat(CashChargePolicy.isCancelable(approvedAt, LocalDateTime.of(2027, 10, 10, 0, 0))).isFalse();
	}
}
