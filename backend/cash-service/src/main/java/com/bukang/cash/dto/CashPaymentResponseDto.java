package com.bukang.cash.dto;

public record CashPaymentResponseDto(Outcome outcome, long shortage) {
	public enum Outcome {
		PAID,
		INSUFFICIENT_BALANCE
	}

	public static CashPaymentResponseDto paid() {
		return new CashPaymentResponseDto(Outcome.PAID, 0);
	}

	public static CashPaymentResponseDto insufficient(long shortage) {
		return new CashPaymentResponseDto(Outcome.INSUFFICIENT_BALANCE, shortage);
	}

	public String message() {
		return outcome() == Outcome.PAID ? "결제했습니다." : "예치금이 부족합니다.";
	}
}
