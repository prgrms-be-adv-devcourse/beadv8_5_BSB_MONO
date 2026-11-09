package com.bukang.cash.domain.exception.payment;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

public class PaymentNotRefundableException extends BusinessException {
	public PaymentNotRefundableException(String message) {
		super(HttpStatus.CONFLICT, message);
	}
}
