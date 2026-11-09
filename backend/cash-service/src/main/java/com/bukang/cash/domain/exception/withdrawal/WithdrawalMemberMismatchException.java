package com.bukang.cash.domain.exception.withdrawal;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

public class WithdrawalMemberMismatchException extends BusinessException {
	public WithdrawalMemberMismatchException(String message) {
		super(HttpStatus.CONFLICT, message);
	}
}
