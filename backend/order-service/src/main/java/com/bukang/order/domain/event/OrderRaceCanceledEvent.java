package com.bukang.order.domain.event;

/**
 * 대회가 취소되어 주문의 결제한 참가권을 취소·환불했다 (PRO-135). 구매자에게 알리는 데 쓴다
 * Order.cancelPaidItems가 발행하고, 환불 트랜잭션 안에서 RaceCanceledNotificationListener가 받아 알림을 요청한다 (알림은 커밋된 뒤에만 나간다)
 */
// itemName: 환불한 참가권 이름(주문 때 복사한 종목 이름). 알림 문구에 쓴다
public record OrderRaceCanceledEvent(long orderId, long buyerId, long raceId, long refundedAmount, String itemName) {
}
