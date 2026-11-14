package com.bukang.order.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 주문 상태 (ERD D3 정리, 10/12 결정)
 * CREATED(자리 확보, 결제 대기) → PAID → PARTIAL_CANCELED / CANCELED. 결제 기한이 지나면 EXPIRED
 * 매진이면 주문을 만들지 않으므로 FAILED는 두지 않는다 (실패는 참가 신청의 FAILED로 남는다)
 */
@Getter
@RequiredArgsConstructor
public enum OrderStatus {
	CREATED("결제 대기"),
	// C안 시제품(측정 브랜치): 판매 수를 센 뒤 잠금을 풀고 예치금·자리 판매를 부르는 동안. 확정하면 PAID, 거절되면 CREATED로 돌아간다
	PAYING("결제 중"),
	PAID("결제 완료"),
	PARTIAL_CANCELED("부분 취소"),
	CANCELED("취소"),
	EXPIRED("기한 만료");

	private final String label;
}
