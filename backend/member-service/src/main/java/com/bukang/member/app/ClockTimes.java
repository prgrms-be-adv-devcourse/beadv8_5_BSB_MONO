package com.bukang.member.app;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 시계 빈은 UTC라 LocalDateTime.now(clock)을 쓰면 서버 시간대와 9시간 어긋난다. instant()만 불러 서버 시간대로 바꾼다
 */
public final class ClockTimes {
	private ClockTimes() {
	}

	public static LocalDateTime now(Clock clock) {
		return LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault());
	}

	public static LocalDate today(Clock clock) {
		return LocalDate.ofInstant(clock.instant(), ZoneId.systemDefault());
	}
}
