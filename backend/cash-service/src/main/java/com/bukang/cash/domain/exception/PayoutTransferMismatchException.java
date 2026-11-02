package com.bukang.cash.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

import lombok.Getter;

/**
 * 같은 정산 ID로 다시 요청했는데 판매자·금액이 이전 처리와 다르다 (PRO-256 리뷰 반영)
 * CashExceptionHandler가 따로 처리해, 이 예외가 들고 있는 저장된(원래 처리된) 금액을 응답 데이터에 담는다.
 */
@Getter
public class PayoutTransferMismatchException extends BusinessException {
	private final long storedSettlementAmount;
	private final long storedFeeAmount;

	public PayoutTransferMismatchException(String message, long storedSettlementAmount, long storedFeeAmount) {
		super(HttpStatus.CONFLICT, message);
		this.storedSettlementAmount = storedSettlementAmount;
		this.storedFeeAmount = storedFeeAmount;
	}
}
