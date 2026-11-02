package com.bukang.cash.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

public class ChargeInProgressException extends BusinessException {
	public ChargeInProgressException(String message) {
		super(HttpStatus.CONFLICT, message);
	}
}
