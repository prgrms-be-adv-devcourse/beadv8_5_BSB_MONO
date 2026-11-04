package com.bukang.cash.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

// message는 RsData의 message로 내보내므로 data에는 다시 넣지 않는다
public record CashChargeResponseDto(Outcome outcome, long balance, @JsonIgnore String message) {
	public static final String DEFAULT_FAILED_MESSAGE = "충전에 실패했습니다.";

	public enum Outcome {
		COMPLETED, FAILED, UNCERTAIN
	}

	public static CashChargeResponseDto completed(long balance) {
		return new CashChargeResponseDto(Outcome.COMPLETED, balance, "충전했습니다.");
	}

	public static CashChargeResponseDto failed(long balance) {
		return failed(balance, DEFAULT_FAILED_MESSAGE);
	}

	// PG가 거절 사유를 알려 줬으면(카드 한도 초과, 결제 시간 만료 등) 그 문구를 그대로 보여 준다
	public static CashChargeResponseDto failed(long balance, String message) {
		return new CashChargeResponseDto(Outcome.FAILED, balance, message);
	}

	// 승인 결과를 끝내 확인하지 못함. 실패가 아니라 "확인 중"이라, 프론트는 잠시 뒤 다시 조회하게 안내한다
	public static CashChargeResponseDto uncertain(long balance) {
		return new CashChargeResponseDto(
			Outcome.UNCERTAIN, balance, "충전 결과를 확인하지 못했습니다. 잠시 후 다시 확인해주세요.");
	}
}
