package com.bukang.payout.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BusinessDayCalendarTest {
	private final BusinessDayCalendar calendar = new BusinessDayCalendar();

	@Test
	@DisplayName("영업일이면 그날을 돌려준다 (2026-10-20 화요일)")
	void nextOrSameKeepsBusinessDay() {
		assertThat(calendar.nextOrSame(LocalDate.of(2026, 10, 20))).isEqualTo(LocalDate.of(2026, 10, 20));
	}

	@Test
	@DisplayName("토요일과 일요일은 다음 월요일로 미룬다 (6/20 토 → 6/22 월, 9/20 일 → 9/21 월)")
	void nextOrSameSkipsWeekend() {
		assertThat(calendar.nextOrSame(LocalDate.of(2026, 6, 20))).isEqualTo(LocalDate.of(2026, 6, 22));
		assertThat(calendar.nextOrSame(LocalDate.of(2026, 9, 20))).isEqualTo(LocalDate.of(2026, 9, 21));
	}
}
