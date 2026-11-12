package com.bukang.cash.domain.exception.withdrawal;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 정리하려는 환급 카드 몫이 없음
public class WithdrawalItemNotFoundException extends BusinessException {
	public WithdrawalItemNotFoundException(String message) {
		super(HttpStatus.NOT_FOUND, "WITHDRAWAL_ITEM_NOT_FOUND", message);
	}
}
