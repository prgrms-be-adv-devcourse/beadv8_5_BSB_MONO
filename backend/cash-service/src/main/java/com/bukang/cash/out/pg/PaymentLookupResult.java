package com.bukang.cash.out.pg;

// PG 결제 조회 결과. approval은 COMPLETED일 때만 있다
public record PaymentLookupResult(Status status, PaymentApprovalResult approval) {
	public enum Status {
		// 승인 완료
		COMPLETED,
		// 승인 안 됨으로 확정 (승인 실패, 만료, 전액 취소, 결제 없음)
		NOT_COMPLETED,
		// 아직 정해지지 않음 (인증만 끝났거나 승인 처리 중). 다시 조회해야 한다
		PENDING
	}

	public static PaymentLookupResult completed(PaymentApprovalResult approval) {
		return new PaymentLookupResult(Status.COMPLETED, approval);
	}

	public static PaymentLookupResult notCompleted() {
		return new PaymentLookupResult(Status.NOT_COMPLETED, null);
	}

	public static PaymentLookupResult pending() {
		return new PaymentLookupResult(Status.PENDING, null);
	}
}
