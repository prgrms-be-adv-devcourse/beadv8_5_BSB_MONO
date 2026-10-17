package com.bukang.payout.domain;

import java.time.LocalDate;

/**
 * 정산 대상일 (피그잼 06 대상일 1번, payout.md 4-3)
 * 1차: 환불 마감일 다음 날. 예: 마감 9/30 → 1차 대상일 10/1, 정산 월 2026-09 → 지급 10/20
 * 최종: 대회 다음 날. 예: 대회 9/25 → 최종 대상일 9/26, 정산 월 2026-09 → 지급 10/20
 * 정산 월은 대상일이 아니라 환불 마감일 · 대회일의 달이다 (월말이면 대상일은 다음 달이 된다)
 */
public final class PayoutTargetDate {
	// 대회 다음 날 (이의 제기 기간 없음. payout.md의 '+3일'은 10/8 제안이라 피그잼 06에 맞춰 고쳤다)
	private static final int DAYS_AFTER_RACE = 1;
	// 환불 마감일 다음 날
	private static final int DAYS_AFTER_REFUND_DEADLINE = 1;

	private PayoutTargetDate() {
	}

	public static LocalDate of(LocalDate raceDate) {
		return raceDate.plusDays(DAYS_AFTER_RACE);
	}

	// 정산 대상일이 targetDate인 대회의 대회일 (배치가 오늘 정산할 대회를 찾을 때 쓴다)
	public static LocalDate raceDateFor(LocalDate targetDate) {
		return targetDate.minusDays(DAYS_AFTER_RACE);
	}

	// 1차 정산 대상일: 환불 마감일 다음 날
	public static LocalDate firstOf(LocalDate refundDeadline) {
		return refundDeadline.plusDays(DAYS_AFTER_REFUND_DEADLINE);
	}

	// 1차 정산 대상일이 targetDate인 대회의 환불 마감일 (1차 배치가 오늘 정산할 대회를 찾을 때 쓴다)
	public static LocalDate refundDeadlineFor(LocalDate targetDate) {
		return targetDate.minusDays(DAYS_AFTER_REFUND_DEADLINE);
	}

	// 정산 대상일 전날까지만 환불할 수 있다. 정산 대상일 당일부터는 대회 취소·일정 변경 환불도 막는다 (payout.md 2-2, PRO-28)
	// MVP는 정산이 한 번이고 회수가 없어서, 정산 내역에 들어간 참가권이 환불되면 판매자에게 그대로 지급되기 때문이다
	// 개인 사정 환불은 그 전에 환불 기한(판매자 지정)에서 막히며, 그 판단은 주문 서비스가 한다
	// 피그잼 정책의 1차·최종 정산과 회수가 들어오면 이 규칙은 '회수 뒤 환불'로 바뀐다
	// 예: 대회 9/25 → 9/25까지 환불 가능, 9/26(정산 대상일)부터 불가
	public static boolean isRefundable(LocalDate raceDate, LocalDate today) {
		return today.isBefore(of(raceDate));
	}
}
