package com.bukang.payout.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 회수 상태. 입금 기한이 지난 연체는 PRO-198에서 더할 수 있다
 */
@Getter
@RequiredArgsConstructor
public enum PayoutRecoveryStatus {
	REQUESTED("회수 요청"),
	COMPLETED("회수 완료"),
	// 받은 돈이 없는 회수를 대회 취소로 되돌림 (1차 지급 전 상계로 끝난 일정 변경 회수, R9)
	CANCELED("회수 취소");

	private final String korean;
}
