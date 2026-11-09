package com.bukang.cash.domain.exception.payment;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

public class PaymentAlreadyFinalizedException extends BusinessException {
	public PaymentAlreadyFinalizedException(String message) {
		super(HttpStatus.CONFLICT, message);
	}
}
