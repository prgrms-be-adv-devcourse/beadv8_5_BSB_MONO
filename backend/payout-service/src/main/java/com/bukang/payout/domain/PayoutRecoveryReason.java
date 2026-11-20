package com.bukang.payout.domain;

import com.bukang.common.shared.order.event.OrderItemRefundReason;

/**
 * 회수 사유 (payout.md 4-2). 회수 1건의 단위가 사유마다 다르다
 * - 대회 취소: 환불된 참가권마다 1건 (BUK-76. 예전 기록은 대회당 1건, 참가권 없는 원장 줄)
 * - 일정 변경: 환불된 참가권(주문 상품)마다 1건, 참가권 원장 줄 (PRO-214)
 */
public enum PayoutRecoveryReason {
	대회_취소,
	일정_변경;

	// 주문의 환불 사유를 회수 사유로 옮긴다. 대회 취소만 대회_취소이고, 나머지(일정 변경 · 구매자 취소)는 일정_변경이다
	public static PayoutRecoveryReason from(OrderItemRefundReason refundReason) {
		return refundReason == OrderItemRefundReason.RACE_CANCELED ? 대회_취소 : 일정_변경;
	}
}
