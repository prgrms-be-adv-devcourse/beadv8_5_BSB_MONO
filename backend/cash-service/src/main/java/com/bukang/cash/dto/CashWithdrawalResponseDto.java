package com.bukang.cash.dto;

// 환급 신청 결과. 정책 5장 2차 1 「PG 결제취소와 기한」대로 돌려준 곳별로 금액을 나눈다:
// 카드 결제취소 요청(cardAmount), 계좌 송금(accountAmount), 처리 중(processingAmount), 예치금에 되돌림(revertedAmount)
public record CashWithdrawalResponseDto(
	long withdrawnAmount,
	long balance,
	long cardAmount,
	long accountAmount,
	long processingAmount,
	long revertedAmount
) {
	public String message() {
		return "환급 신청이 완료되었습니다.";
	}
}
