package com.bukang.payout.domain.exception;

// 회원 서비스에서 판매자 정산 계좌 등록 여부를 받지 못했다. 정산 생성 배치 안에서만 쓰인다 (BUK-60)
public class PayoutSellerAccountLookupUnavailableException extends RuntimeException {
	public PayoutSellerAccountLookupUnavailableException(long sellerId, Throwable cause) {
		super("회원 서비스에서 정산 계좌 등록 여부를 받지 못했습니다. sellerId=%d".formatted(sellerId), cause);
	}
}
