package com.bukang.order.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 택배 1건(배송)의 상태 (BUK-6). 발송 대기 → 배송 중(택배사·송장 번호를 넣으면) → 배송 완료 순서로만 바뀐다
// 발송 대기인 동안 그 안의 참가권이 모두 취소되면 배송 취소로 끝난다. 보낸 뒤에는 취소로 바꾸지 않는다
@Getter
@RequiredArgsConstructor
public enum ShipmentStatus {
	READY("발송 대기", KitDeliveryStatus.PREPARING),
	SHIPPING("배송 중", KitDeliveryStatus.SHIPPING),
	DELIVERED("배송 완료", KitDeliveryStatus.DELIVERED),
	// 화면(키트 배송 상태)에는 배송 준비로 보인다. 배송 표 전에는 발송 전에 취소된 키트가 배송 준비로 남았다
	CANCELED("배송 취소", KitDeliveryStatus.PREPARING);

	private final String label;
	// 판매자·구매자 API가 돌려주는 키트 배송 상태. API 모양을 바꾸지 않으려고 옛 값으로 바꿔 보낸다
	private final KitDeliveryStatus kitDeliveryStatus;

	public static ShipmentStatus from(KitDeliveryStatus kitDeliveryStatus) {
		return switch (kitDeliveryStatus) {
			case PREPARING -> READY;
			case SHIPPING -> SHIPPING;
			case DELIVERED -> DELIVERED;
		};
	}
}
