package com.bukang.cash.domain.exception.payout;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 은행이 정산금을 보냈는지 아직 모른다. 돈이 나갔을 수 있어 은행에 다시 보내지 않고, 재확인이 완료나 실패로 맞춘다
public class PayoutTransferUnknownException extends BusinessException {
	public PayoutTransferUnknownException(String message) {
		super(HttpStatus.SERVICE_UNAVAILABLE, "PAYOUT_TRANSFER_UNKNOWN", message);
	}
}
