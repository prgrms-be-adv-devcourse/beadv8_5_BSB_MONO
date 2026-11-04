package com.bukang.payout.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

public class PayoutClaimNotFoundException extends BusinessException {
	public PayoutClaimNotFoundException() {
		super(HttpStatus.NOT_FOUND, "청구를 찾을 수 없습니다.");
	}
}
