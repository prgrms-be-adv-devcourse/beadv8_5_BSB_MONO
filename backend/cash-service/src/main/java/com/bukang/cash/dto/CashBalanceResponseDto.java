package com.bukang.cash.dto;

/**
 * 내부 잔액 조회 응답 (회원 탈퇴 확인용, BE-022). 지갑이 없는 회원은 0원이다
 */
public record CashBalanceResponseDto(
	long balance
) {
}
