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
	PARTY_PAYMENT_DEADLINE
}
