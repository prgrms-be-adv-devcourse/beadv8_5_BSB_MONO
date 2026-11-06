package com.bukang.common.shared.order.event;

import java.time.LocalDateTime;

/**
 * 참가권 환불 알림. 주문 서비스가 cash 환불을 마친 뒤 보낸다 (선 환불 후 회수, 피그잼 06-1 [6. 판매자 정산 / 3-1]).
 * 정산 서비스가 받아 1차 정산을 받은 참가권이면 판매자에게서 돌려받을 회수를 남긴다.
 * 판매자는 정산이 그 대회의 정산 내역에서 찾으므로 담지 않는다. 주문 상품 ID를 key로 보낸다.
 * 주문이 참가권을 취소로 확정하는 트랜잭션에서 아웃박스에 남기고, 커밋 뒤 릴레이가 보낸다 (BUK-74).
 *
 * @param price 환불된 참가권 한 장의 가격 (주문 상품 가격)
 * @param reason 환불 사유. 비어 있으면 정산은 잘못된 메시지로 보고 처리하지 않는다
 * @param buyerId 환불받은 구매자 회원 ID. 정산은 쓰지 않고, 알림 서비스가 받는 사람으로 쓴다 (BUK-69)
 */
public record OrderItemRefundedEvent(
	long orderItemId,
	long raceId,
	long raceCourseId,
	long price,
	LocalDateTime refundedAt,
	OrderItemRefundReason reason,
	long buyerId
) {
}
