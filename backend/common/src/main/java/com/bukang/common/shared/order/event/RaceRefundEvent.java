package com.bukang.common.shared.order.event;

import java.time.LocalDateTime;

/**
 * 대회 취소 환불의 보류·완료 알림 (order → payout, 11/05 회의 R1). 주문이 대회 취소를 받으면 환불 전에 HELD를,
 * 실패 없이 모두 환불하면 REFUNDED를 보낸다. 금액은 담지 않는다. 판매자에게서 되찾을 돈은 정산이 자기 장부로 센다.
 * 주문 아웃박스로 보내고 key는 대회 ID다.
 *
 * @param refundedCount 대회 취소로 환불한 참가권 수 (REFUNDED만). 정산이 판매 기록과 맞춰 보는 대조용이다. HELD는 0
 * @param occurredAt HELD는 주문이 대회 취소를 받은 시각, REFUNDED는 환불을 끝낸 시각
 */
public record RaceRefundEvent(
	RaceRefundKind kind,
	long raceId,
	long refundedCount,
	LocalDateTime occurredAt
) {
}
