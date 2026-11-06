package com.bukang.payout.domain;

/**
 * 회수 상태. 입금 기한이 지난 연체는 PRO-198에서 더할 수 있다
 */
public enum PayoutRecoveryStatus {
	회수_요청,
	회수_완료,
	// 받은 돈이 없는 회수를 대회 취소로 되돌림 (1차 지급 전 상계로 끝난 일정 변경 회수, R9)
	회수_취소;
}
