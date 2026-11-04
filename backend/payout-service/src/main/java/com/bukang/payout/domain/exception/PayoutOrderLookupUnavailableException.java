package com.bukang.payout.domain.exception;

// 주문 서비스에서 대회의 판매 값(참가권 목록 · 판매 집계)을 받지 못했다. 정산 생성 배치 안에서만 쓰인다 (BUK-60)
public class PayoutOrderLookupUnavailableException extends RuntimeException {
	public PayoutOrderLookupUnavailableException(long raceId, Throwable cause) {
		super("주문 서비스에서 정산용 판매 값을 받지 못했습니다. raceId=%d".formatted(raceId), cause);
	}
}
