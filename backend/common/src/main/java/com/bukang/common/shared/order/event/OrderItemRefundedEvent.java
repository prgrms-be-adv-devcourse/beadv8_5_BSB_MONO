package com.bukang.common.shared.order.event;

import java.time.LocalDateTime;

/**
 * 참가권 환불 알림. 주문 서비스가 cash 환불을 마친 뒤 보낸다 (선 환불 후 회수, 피그잼 06-1 [6. 판매자 정산 / 3-1]).
 * 정산 서비스가 받아 1차 정산을 받은 참가권이면 판매자에게서 돌려받을 회수를 남긴다.
 * 판매자는 정산이 그 대회의 정산 내역에서 찾으므로 담지 않는다. 주문 상품 ID를 key로 보낸다.
 * 발행 쪽 형식은 order 담당과 맞출 때까지 바뀔 수 있다.
 *
 * @param price 환불된 참가권 한 장의 가격 (주문 상품 가격)
 */
public record OrderItemRefundedEvent(
	long orderItemId,
	long raceId,
	long raceCourseId,
	long price,
	LocalDateTime refundedAt
) {
}
