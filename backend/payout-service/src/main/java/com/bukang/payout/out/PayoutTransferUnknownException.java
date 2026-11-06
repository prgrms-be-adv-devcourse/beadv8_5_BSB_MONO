package com.bukang.payout.out;

/**
 * 정산금 이동 요청의 결과를 모른다 (타임아웃 · 연결 실패 · 5xx · 응답 형식 오류) (PRO-252)
 * 캐시 서비스가 돈을 옮겼을 수도 있으므로 정산 내역을 지급 중으로 두고 다음 실행에서 같은 정산 ID로 다시 요청한다
 */
public class PayoutTransferUnknownException extends RuntimeException {
	public PayoutTransferUnknownException(String message, Throwable cause) {
		super(message, cause);
	}
}
