package com.bukang.payout.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PayoutCreateDateTest {

	@Test
	@DisplayName("9/25 대회는 대회 다음 날인 9/26이 정산 생성일이고, 정산 월은 2026-09다")
	void createDateIsDayAfterRace() {
		LocalDate createDate = PayoutCreateDate.of(LocalDate.of(2026, 9, 25));

		assertThat(createDate).isEqualTo(LocalDate.of(2026, 9, 26));
		assertThat(YearMonth.from(createDate)).isEqualTo(YearMonth.of(2026, 9));
	}

	@Test
	@DisplayName("9/30 대회는 정산 생성일이 10/1이라 정산 월이 2026-10이 된다 (월말 경계)")
	void createDateCrossesMonth() {
		LocalDate createDate = PayoutCreateDate.of(LocalDate.of(2026, 9, 30));

		assertThat(createDate).isEqualTo(LocalDate.of(2026, 10, 1));
		assertThat(YearMonth.from(createDate)).isEqualTo(YearMonth.of(2026, 10));
	}

	@Test
	@DisplayName("9/25 대회 참가권은 대회 당일(9/25)까지 환불할 수 있고, 정산 생성일인 9/26부터는 환불할 수 없다")
	void refundableUntilDayBeforeCreateDate() {
		LocalDate raceDate = LocalDate.of(2026, 9, 25);

		assertThat(PayoutCreateDate.isRefundable(raceDate, LocalDate.of(2026, 9, 24))).isTrue();
		assertThat(PayoutCreateDate.isRefundable(raceDate, LocalDate.of(2026, 9, 25))).isTrue();
		assertThat(PayoutCreateDate.isRefundable(raceDate, LocalDate.of(2026, 9, 26))).isFalse();
		assertThat(PayoutCreateDate.isRefundable(raceDate, LocalDate.of(2026, 10, 20))).isFalse();
	}

	@Test
	@DisplayName("정산 생성일이 10/1이면 그날 정산할 대회일은 9/30이다 (배치가 거꾸로 찾을 때)")
	void raceDateForCreateDate() {
		assertThat(PayoutCreateDate.raceDateFor(LocalDate.of(2026, 10, 1))).isEqualTo(LocalDate.of(2026, 9, 30));
	}

	@Test
	@DisplayName("1차 정산 생성일은 환불 마감일 다음 날이다 (마감 9/30 → 10/1, 거꾸로 10/1 → 9/30)")
	void firstCreateDateIsDayAfterRefundDeadline() {
		assertThat(PayoutCreateDate.firstOf(LocalDate.of(2026, 9, 30))).isEqualTo(LocalDate.of(2026, 10, 1));
		assertThat(PayoutCreateDate.refundDeadlineFor(LocalDate.of(2026, 10, 1))).isEqualTo(LocalDate.of(2026, 9, 30));
	}
}
