package com.bukang.common.shared.notification;

/**
 * 알림 종류. 알림 서비스가 종류로 아이콘을 정한다 (문구는 보내는 서비스가 만든다)
 * 새 종류를 더하면 알림 서비스의 아이콘 표(NotificationIcon)도 함께 고친다. 모르는 종류는 받지 못하고 버려진다
 */
public enum NotificationKind {
	// 참가권 취소·환불이 끝났다 (주문)
	REFUND_COMPLETED,
	// 대회가 취소돼 참가비를 돌려줬다 (주문)
	RACE_CANCELED,
	// 대회 일정이 바뀌었다
	RACE_SCHEDULE_CHANGED,
	// 예치금을 계좌로 돌려받았다 (예치금)
	WITHDRAWAL_COMPLETED,
	// 크루에 가입했다 (회원)
	CREW_JOINED,
	// 파티 신청 뒤 파티원에게 결제를 요청한다 (주문, BUK-37)
	PARTY_PAYMENT_REQUESTED,
	// 파티 결제 마감이 다가온다 (주문, BUK-38)
	PARTY_PAYMENT_DEADLINE,
	// 보낸 키트가 7일 동안 택배사에서 조회되지 않아 조회를 멈췄다. 판매자에게 송장 번호를 확인해 달라고 한다 (주문, BUK-90)
	KIT_TRACKING_STOPPED,
	// 환불 기한이 지나 키트를 보낼 참가자가 생겼다. 판매자에게 발송을 요청한다 (주문, 대회마다 한 번)
	KIT_SHIPMENT_REQUIRED,
	// 정산이 보류됐다. 판매자에게 보류 사유를 확인해 달라고 한다 (정산)
	PAYOUT_HELD
}
