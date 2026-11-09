package com.bukang.cash.domain.exception.charge;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

public class ChargeMemberMismatchException extends BusinessException {
	public ChargeMemberMismatchException(String message) {
		super(HttpStatus.CONFLICT, message);
	}
}
