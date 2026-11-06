package com.bukang.order.out;

import com.bukang.order.domain.DeliveryTrackingStatus;

// 조회 배치가 부를 배송 한 건 (BUK-55). 상대를 부르는 동안 주문을 잠그지 않으려고 값만 먼저 읽는다
// trackingStatus는 조회에 실패했을 때 다음 조회 시각을 정하는 데 쓴다 (BUK-90)
public record ShipmentTrackingTarget(long shipmentId, long orderId, String carrierId, String trackingNumber,
	DeliveryTrackingStatus trackingStatus) {
}
