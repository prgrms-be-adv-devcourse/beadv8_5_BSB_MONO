package com.bukang.order.out.delivery;

import java.time.LocalDateTime;
import java.util.List;

import com.bukang.order.domain.DeliveryTrackingStatus;

/**
 * 운송장 하나를 조회한 결과 (BUK-55)
 * status·statusText·lastEventAt은 Delivery Tracker의 lastEvent(가장 그럴듯한 최근 상태)다. events는 오래된 것부터다
 */
public record DeliveryTrackingResult(
	DeliveryTrackingStatus status,
	String statusText,
	LocalDateTime lastEventAt,
	List<Event> events
) {
	// 택배사가 아직 스캔하지 않아 조회되지 않는 운송장
	public static DeliveryTrackingResult notTracked() {
		return new DeliveryTrackingResult(DeliveryTrackingStatus.NOT_TRACKED, null, null, List.of());
	}

	public boolean isTracked() {
		return status != DeliveryTrackingStatus.NOT_TRACKED;
	}

	// 이동 기록 한 줄. 시각·설명·위치는 택배사가 주지 않으면 비어 있다
	public record Event(LocalDateTime time, DeliveryTrackingStatus status, String statusText, String description,
		String location) {
	}
}
