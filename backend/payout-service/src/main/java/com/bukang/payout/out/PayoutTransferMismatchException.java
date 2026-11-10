package com.bukang.payout.out;

/**
 * 캐시 서비스가 같은 정산 ID를 이미 다른 판매자 · 금액으로 처리했다며 409로 답했다 (BUK-62)
 * 돈은 캐시 서비스에 저장된 금액으로 이미 나갔다. 다시 요청해도 같은 409가 나오므로 지급 불일치로 멈추고 사람이 확인한다
 * 메시지에 캐시 서비스 응답 본문(저장된 금액)을 담는다
 */
public class PayoutTransferMismatchException extends RuntimeException {
	public PayoutTransferMismatchException(String message, Throwable cause) {
		super(message, cause);
	}
}
