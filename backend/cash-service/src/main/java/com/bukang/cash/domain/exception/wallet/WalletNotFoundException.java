package com.bukang.cash.domain.exception.wallet;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

public class WalletNotFoundException extends BusinessException {
	public WalletNotFoundException(String message) {
		super(HttpStatus.NOT_FOUND, message);
	}
}
