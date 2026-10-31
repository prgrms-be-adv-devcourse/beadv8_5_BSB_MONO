package com.bukang.payout.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

/**
 * 지금 상태로는 청구를 처리할 수 없을 때 (이미 입금을 확인함)
 */
public class PayoutClaimConflictException extends BusinessException {
	public PayoutClaimConflictException(String message) {
		super(HttpStatus.CONFLICT, message);
	}
}
