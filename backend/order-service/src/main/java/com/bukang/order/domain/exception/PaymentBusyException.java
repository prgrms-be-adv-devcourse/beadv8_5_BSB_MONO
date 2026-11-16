package com.bukang.order.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

/**
 * 예치금 결제 동시 호출 자리가 다 차 기다리다 못 받음 (BUK-235). 예치금을 부르지 않았으므로 돈이 빠지지 않은 것이 확실한 실패다
 * 요청은 판매 수·결제 중을 되돌리고 503으로 끝낸다. 결제 재확인 배치는 결제 중으로 두고 다음 회차에 다시 본다
 */
public class PaymentBusyException extends BusinessException {
	public static final String CODE = "ORDER_PAYMENT_BUSY";
	public static final String MESSAGE = "결제 요청이 몰려 있습니다. 잠시 후 다시 시도해주세요.";

	public PaymentBusyException() {
		super(HttpStatus.SERVICE_UNAVAILABLE, CODE, MESSAGE);
	}
}
