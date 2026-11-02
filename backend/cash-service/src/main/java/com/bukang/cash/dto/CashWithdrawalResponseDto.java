package com.bukang.cash.dto;

public record CashWithdrawalResponseDto(long withdrawnAmount, long balance) {
	public static CashWithdrawalResponseDto of(long withdrawnAmount, long balance) {
		return new CashWithdrawalResponseDto(withdrawnAmount, balance);
	}

	public String message() {
		return "환급 신청이 완료되었습니다.";
	}
}
