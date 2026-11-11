package com.bukang.cash.domain.recovery;

// 회수 입금의 진행 상태
public enum RecoveryDepositStatus {
	// 가상계좌를 발급했고 판매자의 입금을 기다린다
	WAITING,
	// PG에서 입금 완료를 확인했고 홀딩 지갑에 기록했다
	DEPOSITED,
	// 입금 기한이 지나 PG가 계좌를 닫았다. 관리자가 판매자와 직접 맞춘다
	EXPIRED
}
