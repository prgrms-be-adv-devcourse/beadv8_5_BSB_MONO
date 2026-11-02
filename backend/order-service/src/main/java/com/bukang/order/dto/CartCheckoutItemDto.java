package com.bukang.order.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 장바구니 결제에서 참가 신청 하나의 결과. 항목마다 따로 처리하므로 일부만 성공할 수 있다
 */
@AllArgsConstructor
@Getter
public class CartCheckoutItemDto {
	public enum Outcome {
		// 자리를 확보해 결제 대기 주문을 만들었다
		CREATED,
		// 같은 요청으로 이미 만든 주문 (재시도)
		EXISTING,
		// 남은 자리가 없다. 참가 신청은 FAILED가 되고 장바구니 항목은 남는다
		SOLD_OUT,
		// 접수 기간이 아니거나 결제할 수 없는 신청, 처리 중 오류 등. 장바구니 항목은 남는다
		FAILED
	}

	private final long participationId;
	private final Outcome outcome;
	// SOLD_OUT·FAILED일 때의 오류 코드 (단건 API와 같은 값, 예: ORDER_RACE_CANCELED). 성공이면 응답에서 빠진다
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private final String code;
	private final String message;
	// CREATED·EXISTING일 때만 있다
	private final OrderDto order;
}
