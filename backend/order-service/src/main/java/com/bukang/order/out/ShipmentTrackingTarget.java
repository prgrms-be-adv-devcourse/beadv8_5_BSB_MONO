package com.bukang.order.out;

// 조회 배치가 부를 배송 한 건 (BUK-55). 상대를 부르는 동안 주문을 잠그지 않으려고 값만 먼저 읽는다
public record ShipmentTrackingTarget(long shipmentId, long orderId, String carrierId, String trackingNumber) {
}
