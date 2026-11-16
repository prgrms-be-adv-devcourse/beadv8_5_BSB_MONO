package com.bukang.cash.domain.exception.payment;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 같은 주문 ID로 처음과 다른 금액이 오면 거절한다. 그대로 빼면 뺀 돈과 취소·환불로 돌려주는 돈(결제 기록 금액)이 달라진다
public class PaymentAmountMismatchException extends BusinessException {
	public PaymentAmountMismatchException(String message) {
		super(HttpStatus.CONFLICT, message);
	}
}
