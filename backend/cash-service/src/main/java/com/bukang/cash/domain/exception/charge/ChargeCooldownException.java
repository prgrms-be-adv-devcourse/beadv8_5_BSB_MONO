package com.bukang.cash.domain.exception.charge;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

public class ChargeCooldownException extends BusinessException {
	public ChargeCooldownException(String message) {
		super(HttpStatus.TOO_MANY_REQUESTS, message);
	}
}
