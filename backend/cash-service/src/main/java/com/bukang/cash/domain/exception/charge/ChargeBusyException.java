package com.bukang.cash.domain.exception.charge;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 충전 확정이 동시에 너무 많아 토스를 부르기 전에 거절했다. 돈이 빠지지 않은 게 확실해서
// 프론트가 「다시 결제」로 안내하는 4xx(429)로 답한다. 5xx·409는 프론트가 「결과를 모름」으로 본다
public class ChargeBusyException extends BusinessException {
	public ChargeBusyException() {
		super(HttpStatus.TOO_MANY_REQUESTS, "CHARGE_BUSY", "충전 요청이 많아 지금은 처리할 수 없습니다. 잠시 후 다시 시도해주세요.");
	}
}
