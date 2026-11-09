package com.bukang.cash.domain.exception.charge;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

import lombok.Getter;

/**
 *  PG(Toss페이먼츠)에 결제 승인을 요청했으나 승인 완료 응답을 받지 못함 (연결 실패, 응답 시간 초과, 5xx, 거절 응답)
 *  거절이 확정된 경우 message는 사용자에게 그대로 보여 줄 문구다
 * Toss가 거절 응답({code, message})에 담아 보낸 자체 오류 코드 (예: ALREADY_PROCESSED_PAYMENT)
 */
@Getter
public class PaymentApprovalFailedException extends BusinessException {
	private final String pgCode;
	private final boolean resultUnknown;

	// 응답 자체를 받지 못함: 승인됐는지 알 수 없다
	public PaymentApprovalFailedException(String message) {
		this(message, null, true);
	}

	public PaymentApprovalFailedException(String message, String pgCode, boolean resultUnknown) {
		super(HttpStatus.SERVICE_UNAVAILABLE, message);
		this.pgCode = pgCode;
		this.resultUnknown = resultUnknown;
	}
}
