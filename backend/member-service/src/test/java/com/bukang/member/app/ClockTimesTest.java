package com.bukang.member.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 시계 빈은 UTC라 LocalDateTime.now(clock)으로 되돌리면 서버 시간대와 어긋난다. 그것을 잡는다
class ClockTimesTest {
	private static final Instant INSTANT = Instant.parse("2026-11-18T20:30:00Z");

	@Test
	@DisplayName("UTC 시계도 서버 시간대의 날짜와 시각으로 바꾼다")
	void convertsToServerZone() {
		Clock clock = Clock.fixed(INSTANT, ZoneOffset.UTC);

		assertThat(ClockTimes.now(clock)).isEqualTo(LocalDateTime.ofInstant(INSTANT, ZoneId.systemDefault()));
		assertThat(ClockTimes.today(clock)).isEqualTo(LocalDate.ofInstant(INSTANT, ZoneId.systemDefault()));
	}

	@Test
	@DisplayName("instant()만 정해 둔 시계 목으로도 NPE가 나지 않는다")
	void onlyInstantIsCalled() {
		Clock clock = mock(Clock.class);
		when(clock.instant()).thenReturn(INSTANT);

		assertThat(ClockTimes.now(clock)).isEqualTo(LocalDateTime.ofInstant(INSTANT, ZoneId.systemDefault()));
		assertThat(ClockTimes.today(clock)).isEqualTo(LocalDate.ofInstant(INSTANT, ZoneId.systemDefault()));
	}
}
