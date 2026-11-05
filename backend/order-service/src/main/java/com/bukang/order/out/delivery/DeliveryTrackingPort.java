package com.bukang.order.out.delivery;

/**
 * 운송장 배송 상태 조회 (BUK-55). 지금은 Delivery Tracker(HttpDeliveryTrackingAdapter)이고, 택배사 공식 API로 바꿀 수 있게 포트로 둔다
 * 읽기만 하므로 여러 번 불러도 된다
 * - 운송장이 아직 없음(택배사가 스캔 전): 예외가 아니라 DeliveryTrackingResult.notTracked()
 * - 상대를 부르지 못함(시간 초과·인증·5xx·errors): ExternalServiceUnavailableException
 */
public interface DeliveryTrackingPort {
	DeliveryTrackingResult track(String carrierId, String trackingNumber);
}
