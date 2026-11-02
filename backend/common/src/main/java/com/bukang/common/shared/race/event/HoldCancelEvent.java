package com.bukang.common.shared.race.event;

/**
 * 임시 확보 해제 요청. 대기열 활성 시간이 끝났는데 결제하지 않은 자리를, 주문 서비스가 커밋한 뒤 발행한다.
 * 상품 서비스가 받아 결제 대기 자리를 해제하고 정원으로 되돌린다. 이미 판매·해제된 자리면 아무것도 하지 않는다.
 * 같은 자리의 메시지가 순서대로 처리되도록 참가 신청 ID를 key로 보낸다.
 */
public record HoldCancelEvent(
	// 자리를 확보할 때 보낸 참가 신청 ID
	long participationId
) {
}
