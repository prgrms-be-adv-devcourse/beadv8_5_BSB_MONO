package com.bukang.cash.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 같은 지갑에 처리 중인 환급이 있어 새 환급을 신청할 수 없음. 한 번에 하나의 환급만 진행해 금액과 내역을 분명히 한다
public class WithdrawalInProgressException extends BusinessException {
	public WithdrawalInProgressException(String message) {
		super(HttpStatus.CONFLICT, "WITHDRAWAL_IN_PROGRESS", message);
	}
}
