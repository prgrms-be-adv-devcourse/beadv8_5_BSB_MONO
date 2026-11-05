package com.bukang.cash.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

import lombok.Getter;

// PG(Toss페이먼츠)에 결제 취소를 요청했으나 실패함 (연결 실패, 응답 시간 초과, 5xx, 거절 응답)
@Getter
public class PaymentCancelFailedException extends BusinessException {
	// Toss가 거절 응답({code, message})에 담아 보낸 자체 오류 코드 (예: ALREADY_CANCELED_PAYMENT).
	// 연결 실패처럼 Toss 응답 자체가 없는 경우엔 null이다.
	private final String pgCode;

	public PaymentCancelFailedException(String message) {
		this(message, null);
	}

	public PaymentCancelFailedException(String message, String pgCode) {
		super(HttpStatus.SERVICE_UNAVAILABLE, message);
		this.pgCode = pgCode;
	}
}
