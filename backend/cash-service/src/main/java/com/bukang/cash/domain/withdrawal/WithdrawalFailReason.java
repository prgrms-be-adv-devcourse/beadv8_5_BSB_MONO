package com.bukang.cash.domain.withdrawal;

// 환급 상태가 FAILED일 때만 채운다
public enum WithdrawalFailReason {
	// 카드·계좌로 돌려준 몫이 없이 신청 금액 전부를 예치금에 되돌림
	ALL_REVERTED,
	INVALID_ACCOUNT
}
