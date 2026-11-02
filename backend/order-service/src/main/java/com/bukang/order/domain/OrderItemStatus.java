package com.bukang.order.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 주문 상품 상태 (09 ERD order_item.status). 취소는 주문 상품 단위로 한다 (PRO-46)
// 취소 중: 취소를 시작했지만 예치금 환불 결과를 아직 모른다. 환불이 확인되면 취소, 거절되면 결제 완료로 돌아간다 (BUK-9)
@Getter
@RequiredArgsConstructor
public enum OrderItemStatus {
	ORDERED("결제 대기"),
	PAID("결제 완료"),
	CANCELING("취소 중"),
	CANCELED("취소");

	private final String label;
}
