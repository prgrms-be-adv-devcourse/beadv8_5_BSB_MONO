package com.bukang.payout.domain;

/**
 * 회수 사유 (payout.md 4-2). 회수 1건의 단위가 사유마다 다르다
 * - 대회 취소: 대회당 1건, 참가권 없는 원장 줄
 * - 일정 변경: 환불된 참가권(주문 상품)마다 1건, 참가권 원장 줄 (PRO-214)
 */
public enum PayoutRecoveryReason {
	대회_취소,
	일정_변경;
}
