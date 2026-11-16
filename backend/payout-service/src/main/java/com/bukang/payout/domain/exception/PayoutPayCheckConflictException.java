package com.bukang.payout.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 지급 확인 필요가 아닌 정산 내역을 닫으려 했다 (BUK-174). 이미 닫혔거나 처음부터 이 상태가 아니다
public class PayoutPayCheckConflictException extends BusinessException {
	public PayoutPayCheckConflictException() {
		super(HttpStatus.CONFLICT, "PAYOUT_NOT_PAY_CHECK", "지급 확인이 필요한 정산 내역이 아닙니다.");
	}
}
