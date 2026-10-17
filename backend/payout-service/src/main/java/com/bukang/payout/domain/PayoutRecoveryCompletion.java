package com.bukang.payout.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 회수가 어떻게 끝났는지 (PRO-214)
 * 상계로 끝난 회수는 사람이 입금을 확인한 게 아니므로 입금과 구분해 남긴다
 */
@Getter
@RequiredArgsConstructor
public enum PayoutRecoveryCompletion {
	// 판매자 입금을 확인해 끝남 (지금은 관리자 확인, 판매자 입금 요청 · 시스템 확인 이슈에서 바뀐다)
	DEPOSIT("입금"),
	// 지급 전 정산 내역에서 빼서 끝남
	OFFSET("상계");

	private final String korean;
}
