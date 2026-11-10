package com.bukang.cash.domain.exception.wallet;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

public class WalletBalanceEmptyException extends BusinessException {
	public WalletBalanceEmptyException(String message) {
		super(HttpStatus.BAD_REQUEST, message);
	}
}
