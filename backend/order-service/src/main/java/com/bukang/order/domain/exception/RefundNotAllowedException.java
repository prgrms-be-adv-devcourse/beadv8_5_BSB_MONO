package com.bukang.order.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 환불할 수 없는 참가권 (환불 기한 지남, 정산 생성일 지남)
public class RefundNotAllowedException extends BusinessException {
	public static final String CODE = "ORDER_REFUND_PERIOD_PASSED";
	public static final String PAYOUT_STARTED_CODE = "ORDER_PAYOUT_STARTED";

	public RefundNotAllowedException(String message) {
		this(CODE, message);
	}

	private RefundNotAllowedException(String code, String message) {
		super(HttpStatus.CONFLICT, code, message);
	}

	// 정산 생성일이 지나 정산이 시작됨
	public static RefundNotAllowedException payoutStarted(String message) {
		return new RefundNotAllowedException(PAYOUT_STARTED_CODE, message);
	}
}
