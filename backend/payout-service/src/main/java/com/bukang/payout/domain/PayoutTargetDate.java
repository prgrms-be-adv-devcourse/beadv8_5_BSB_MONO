package com.bukang.payout.domain;

import java.time.LocalDate;

/**
 * 정산 대상일: 참가권은 대회가 끝난 다음 날에 정산 대상이 된다 (피그잼 06 정책 최종안 · 1차 2장)
 * 예: 대회 9/25 → 정산 대상일 9/26 → 지급 10/20
 */
public final class PayoutTargetDate {
	// 대회 다음 날
	private static final int DAYS_AFTER_RACE = 1;

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
