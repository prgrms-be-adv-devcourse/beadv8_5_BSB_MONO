package com.bukang.payout.domain;

import java.time.LocalDate;

/**
 * 정산 대상일: 참가권은 대회 다음 날부터 3일의 이의 제기 기간이 지난 날에 정산 대상이 된다 (payout.md 2-4)
 * 예: 대회 9/25 → 이의 제기 9/26~9/28 → 정산 대상일 9/29
 */
public final class PayoutTargetDate {
	// 대회 다음 날(1일) + 이의 제기 3일
	private static final int DAYS_AFTER_RACE = 1 + 3;

	private PayoutTargetDate() {
	}

	public static LocalDate of(LocalDate raceDate) {
		return raceDate.plusDays(DAYS_AFTER_RACE);
	}

	// 정산 대상일이 targetDate인 대회의 대회일 (배치가 오늘 정산할 대회를 찾을 때 쓴다)
	public static LocalDate raceDateFor(LocalDate targetDate) {
		return targetDate.minusDays(DAYS_AFTER_RACE);
	}
}
