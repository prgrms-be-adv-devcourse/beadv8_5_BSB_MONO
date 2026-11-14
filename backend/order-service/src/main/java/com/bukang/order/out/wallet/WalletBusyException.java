package com.bukang.order.out.wallet;

import com.bukang.order.domain.exception.ExternalServiceRejectedException;

/**
 * 예치금 결제 동시 호출 자리를 기다리다 못 받음 (C안 시제품). 예치금을 부르지 않았으므로 확실한 실패다
 * 요청은 결제 중을 되돌리고 503으로 끝내고, 결제 재확인 배치는 결제 중으로 두고 다음 실행에서 다시 본다
 */
public class WalletBusyException extends ExternalServiceRejectedException {
	public WalletBusyException(String message) {
		super(message);
	}
}
