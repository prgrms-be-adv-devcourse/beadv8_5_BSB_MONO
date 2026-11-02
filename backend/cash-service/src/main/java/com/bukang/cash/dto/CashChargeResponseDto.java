package com.bukang.cash.dto;

public record CashChargeResponseDto(Outcome outcome, long balance) {
	public enum Outcome {
		COMPLETED, FAILED
	}

	public static CashChargeResponseDto completed(long balance) {
		return new CashChargeResponseDto(Outcome.COMPLETED, balance);
	}

	public static CashChargeResponseDto failed(long balance) {
		return new CashChargeResponseDto(Outcome.FAILED, balance);
	}

	public String message() {
		return outcome() == Outcome.COMPLETED ? "충전했습니다." : "충전에 실패했습니다.";
	}
}
