package com.bukang.cash.domain.exception.seller;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

public class CashSellerNotFoundException extends BusinessException {
	public CashSellerNotFoundException(String message) {
		super(HttpStatus.NOT_FOUND, message);
	}
}
