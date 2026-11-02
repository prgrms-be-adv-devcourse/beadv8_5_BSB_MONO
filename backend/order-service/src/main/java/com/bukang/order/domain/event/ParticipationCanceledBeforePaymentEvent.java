package com.bukang.order.domain.event;

/**
 * [결제하기] 전 신청서를 취소했다 (Participation.cancelBeforePayment가 발행하는 스프링 이벤트)
 * 결제 전 신청서도 상품 서비스에 자리가 잡혀 있을 수 있다. [결제하기]에서 상품은 자리를 잡아 커밋했는데 주문 저장이 롤백되면
 * 신청서는 PENDING인 채 자리만 남는다. ParticipationCanceledEventListener가 같은 트랜잭션 안에서 자리 해제 메시지를 아웃박스에 남긴다
 *
 * @param participationId 취소한 참가 신청 ID (자리를 확보할 때 상품 서비스에 보낸 ID)
 */
public record ParticipationCanceledBeforePaymentEvent(long participationId) {
}
