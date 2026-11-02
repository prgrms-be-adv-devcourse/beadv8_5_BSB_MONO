package com.bukang.common.shared.order.event;

/**
 * 참가권이 왜 환불됐는지 (OrderItemRefundedEvent). 정산은 사유에 따라 회수를 남길지 정한다
 * 대회 취소 환불은 정산이 대회 취소 알림(order.race.cancel)으로 따로 회수하므로, 이 알림으로는 회수를 남기지 않는다
 */
public enum OrderItemRefundReason {
	// 대회 일정이 바뀐 뒤 구매자가 환불했다
	SCHEDULE_CHANGED,
	// 환불 기한 안에 구매자가 취소했다
	BUYER_CANCELED,
	// 대회가 취소돼 참가권을 일괄 환불했다
	RACE_CANCELED
}
