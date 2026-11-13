package com.bukang.cash.domain.exception.payout;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 은행이 정산금 송금을 거절했다. 홀딩 지갑 차감은 되돌렸으니, 정산은 같은 정산 ID로 다시 요청하면 새로 송금한다
public class PayoutTransferFailedException extends BusinessException {
	public PayoutTransferFailedException(String message) {
		super(HttpStatus.UNPROCESSABLE_ENTITY, "PAYOUT_TRANSFER_FAILED", message);
	}
}
