package com.bukang.order.domain;

import java.time.Duration;
import java.util.Arrays;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// Delivery Tracker에서 받아 온 배송 상태 (BUK-55). NOT_TRACKED 말고는 Delivery Tracker의 TrackEventStatusCode와 같은 이름이다
// 키트 배송 상태(KitDeliveryStatus)와 따로 둔다. 택배사 상태는 오가며 바뀌고, 키트는 DELIVERED일 때만 배송 완료로 바꾼다
@Getter
@RequiredArgsConstructor
public enum DeliveryTrackingStatus {
	// 발송 직후처럼 택배사가 아직 스캔하지 않아 조회되지 않음 (Delivery Tracker NOT_FOUND)
	NOT_TRACKED("아직 조회되지 않음"),
	INFORMATION_RECEIVED("접수"),
	AT_PICKUP("집화"),
	IN_TRANSIT("이동 중"),
	OUT_FOR_DELIVERY("배달 출발"),
	ATTEMPT_FAIL("배달 실패"),
	DELIVERED("배달 완료"),
	AVAILABLE_FOR_PICKUP("수령 대기"),
	EXCEPTION("배송 문제"),
	UNKNOWN("알 수 없음");

	// 배달이 곧 끝나는 상태는 30분, 그 밖은 3시간 뒤에 다시 조회한다 (BUK-90, 11/05 R4. 간격 값은 리뷰 때 확정)
	// 30분 고정이면 3일 배송 1건에 144번이라 Delivery Tracker 무료 한도(월 1만 건)로 약 70건만 버틴다
	private static final Duration NEAR_DELIVERY_INTERVAL = Duration.ofMinutes(30);
	private static final Duration DEFAULT_INTERVAL = Duration.ofHours(3);

	private final String label;

	// 이 상태를 받은 뒤 다음 조회까지 둘 시간
	public Duration trackingInterval() {
		return switch (this) {
			case OUT_FOR_DELIVERY, ATTEMPT_FAIL, AVAILABLE_FOR_PICKUP -> NEAR_DELIVERY_INTERVAL;
			default -> DEFAULT_INTERVAL;
		};
	}

	// Delivery Tracker 상태 코드. 모르는 코드(새로 생긴 값)는 UNKNOWN으로 본다
	public static DeliveryTrackingStatus fromCode(String code) {
		return Arrays.stream(values())
			.filter(status -> status != NOT_TRACKED && status.name().equals(code))
			.findFirst()
			.orElse(UNKNOWN);
	}
}
