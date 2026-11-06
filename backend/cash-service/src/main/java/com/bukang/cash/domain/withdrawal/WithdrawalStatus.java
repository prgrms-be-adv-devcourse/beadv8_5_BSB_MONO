package com.bukang.cash.domain.withdrawal;

public enum WithdrawalStatus {
	REQUESTED,
	// PG 결제취소 결과를 기다리는 몫이 남아 있음. 이 동안 같은 지갑의 새 환급은 막는다
	IN_PROGRESS,
	COMPLETED,
	FAILED
}
