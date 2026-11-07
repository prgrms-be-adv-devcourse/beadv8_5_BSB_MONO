package com.bukang.cash.domain.exception.charge;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

public class ChargeAmountTooSmallException extends BusinessException {
	public ChargeAmountTooSmallException(String message) {
		super(HttpStatus.BAD_REQUEST, message);
	}
}
