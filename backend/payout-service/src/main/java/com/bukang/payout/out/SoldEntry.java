package com.bukang.payout.out;

/**
 * 정산할 참가권 1장 (결제되고 취소되지 않은 주문 상품)
 * 주문 서비스의 order_item에서 정산에 필요한 값만 옮긴 것이다
 */
public record SoldEntry(int orderItemId, int sellerId, int raceId, long price) {
}
