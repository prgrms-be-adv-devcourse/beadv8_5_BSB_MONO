package com.bukang.common.shared.order.event;

/**
 * 대회 취소 환불 메시지의 종류 (RaceRefundEvent). 같은 토픽에 보내고 key는 대회 ID라 같은 대회는 보낸 순서대로 도착한다
 */
public enum RaceRefundKind {
	// 대회가 취소돼 주문이 구매자 환불을 시작한다. 정산은 그 대회 지급을 막는다
	HELD,
	// 그 대회의 결제한 참가권을 실패 없이 모두 환불했다. 정산은 보류를 풀고 닫기·회수를 한다
	REFUNDED
}
