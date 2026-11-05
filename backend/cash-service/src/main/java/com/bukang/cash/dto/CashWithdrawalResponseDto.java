package com.bukang.cash.dto;

// 환급 신청 결과. 정책 5장 2차 1 「PG 결제취소와 기한」대로 돌려준 곳별로 금액을 나눈다:
// 카드 결제취소 요청(cardAmount), 계좌 송금(accountAmount), 처리 중(processingAmount), 예치금에 되돌림(revertedAmount)
// accountRequired가 true면 카드로 돌려줄 수 없는 몫이 계좌 정보가 없어 예치금에 되돌아갔다. 계좌 정보를 넣고 다시 신청하게 한다
public record CashWithdrawalResponseDto(
	long withdrawnAmount,
	long balance,
	long cardAmount,
	long accountAmount,
	long processingAmount,
	long revertedAmount,
	boolean accountRequired
) {
	public String message() {
		return "환급 신청이 완료되었습니다.";
	}
}
