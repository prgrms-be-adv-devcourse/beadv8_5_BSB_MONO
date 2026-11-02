package com.bukang.order.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

import lombok.Getter;

// 예치금이 부족함. 주문과 자리는 그대로 두고 충전 뒤 같은 주문으로 다시 결제한다
// 부족 금액은 응답 data.shortage로도 준다 (OrderExceptionHandler). 프론트가 문구를 읽지 않고 충전 금액을 채울 수 있게 한다
@Getter
public class InsufficientBalanceException extends BusinessException {
	public static final String CODE = "ORDER_INSUFFICIENT_BALANCE";

	private final long shortage;

	public InsufficientBalanceException(long shortage) {
		super(HttpStatus.CONFLICT, CODE,
			"예치금이 %,d원 부족합니다. 충전한 뒤 결제 기한 안에 다시 결제해주세요.".formatted(shortage));
		this.shortage = shortage;
	}
}
