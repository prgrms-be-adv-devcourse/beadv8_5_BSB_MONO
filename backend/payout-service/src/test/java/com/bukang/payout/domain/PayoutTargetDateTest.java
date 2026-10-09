package com.bukang.payout.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PayoutTargetDateTest {

	@Test
	@DisplayName("9/25 대회는 이의 제기 기간(9/26~9/28)이 지난 9/29가 정산 대상일이고, 정산 월은 2026-09다")
	void targetDateAfterObjectionPeriod() {
		LocalDate targetDate = PayoutTargetDate.of(LocalDate.of(2026, 9, 25));

		assertThat(targetDate).isEqualTo(LocalDate.of(2026, 9, 29));
		assertThat(YearMonth.from(targetDate)).isEqualTo(YearMonth.of(2026, 9));
	}

	@Test
	@DisplayName("9/28 대회는 정산 대상일이 10/2라 정산 월이 2026-10이 된다 (월말 경계)")
	void targetDateCrossesMonth() {
		LocalDate targetDate = PayoutTargetDate.of(LocalDate.of(2026, 9, 28));

		assertThat(targetDate).isEqualTo(LocalDate.of(2026, 10, 2));
		assertThat(YearMonth.from(targetDate)).isEqualTo(YearMonth.of(2026, 10));
	}

	@Test
	@DisplayName("정산 대상일이 10/2이면 그날 정산할 대회일은 9/28이다 (배치가 거꾸로 찾을 때)")
	void raceDateForTargetDate() {
		assertThat(PayoutTargetDate.raceDateFor(LocalDate.of(2026, 10, 2))).isEqualTo(LocalDate.of(2026, 9, 28));
	}
}
