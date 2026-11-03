package com.bukang.order.domain;

import java.util.Arrays;
import java.util.Optional;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 키트를 보낼 수 있는 택배사 (BUK-55). id는 Delivery Tracker 택배사 ID다 (https://tracker.delivery/carriers)
// 목록에 없는 택배사는 배송 상태를 받아 올 수 없어 발송을 받지 않는다
@Getter
@RequiredArgsConstructor
public enum DeliveryCarrier {
	CJ_LOGISTICS("kr.cjlogistics", "CJ대한통운", false),
	EPOST("kr.epost", "우체국택배", false),
	HANJIN("kr.hanjin", "한진택배", false),
	LOTTE("kr.lotte", "롯데택배", false),
	LOGEN("kr.logen", "로젠택배", false),
	KDEXP("kr.kdexp", "경동택배", false),
	DAESIN("kr.daesin", "대신택배", false),
	ILYANG("kr.ilyanglogis", "일양로지스", false),
	CVSNET("kr.cvsnet", "GS Postbox 택배", false),
	CU_POST("kr.cupost", "CU 편의점택배", false),
	COUPANG("kr.coupangls", "쿠팡 로지스틱스", false),
	// Delivery Tracker의 개발용 가짜 택배사. 운송장 번호가 시각(예: 2026-11-03T12:00:00Z)이고 그 시각에 배송 완료가 된다
	DUMMY("dev.track.dummy", "가짜 택배사 (개발용)", true);

	private final String id;
	private final String label;
	// 개발 설정(order.clients.delivery-tracker.dummy-carrier-enabled)에서만 고를 수 있는 택배사
	private final boolean devOnly;

	// 고를 수 있는 택배사인지. 목록에 없거나, 개발용인데 개발 설정이 아니면 비어 있다
	public static Optional<DeliveryCarrier> find(String id, boolean devCarrierEnabled) {
		return Arrays.stream(values())
			.filter(carrier -> carrier.id.equals(id))
			.filter(carrier -> !carrier.devOnly || devCarrierEnabled)
			.findFirst();
	}
}
