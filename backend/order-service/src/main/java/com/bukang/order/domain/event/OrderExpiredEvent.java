package com.bukang.order.domain.event;

/**
 * 결제하지 않고 기한이 지나 주문을 만료했다 (Order.expire가 발행하는 스프링 이벤트)
 * OrderExpiredEventListener가 같은 트랜잭션 안에서 자리 해제 메시지를 아웃박스에 남기고, 커밋 뒤 Kafka로 상품 서비스에 간다
 *
 * @param orderId         만료한 주문 ID
 * @param participationId 자리를 확보할 때 상품 서비스에 보낸 참가 신청 ID
 */
public record OrderExpiredEvent(long orderId, long participationId) {
}
