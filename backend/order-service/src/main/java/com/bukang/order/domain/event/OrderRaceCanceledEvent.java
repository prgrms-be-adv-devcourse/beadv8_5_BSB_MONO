package com.bukang.order.domain.event;

/**
 * 대회가 취소되어 주문의 결제한 참가권을 취소·환불했다 (PRO-135). 구매자에게 알리는 데 쓴다
 * Order.cancelPaidItems가 발행하고, 환불까지 커밋된 뒤에만 RaceCanceledNotificationListener가 받는다
 */
public record OrderRaceCanceledEvent(long orderId, long buyerId, long raceId, long refundedAmount) {
}
