package com.bukang.payout.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 회수 사유 (payout.md 4-2). 회수 1건의 단위가 사유마다 다르다
 * - 대회 취소: 대회당 1건, 묶음째 원장 줄
 * - 일정 변경: 환불된 참가권(주문 상품)마다 1건, 참가권 원장 줄 (PRO-214)
 */
@Getter
@RequiredArgsConstructor
public enum PayoutRecoveryReason {
	RACE_CANCELED("대회 취소"),
	SCHEDULE_CHANGED("일정 변경");

	private final String korean;
}
