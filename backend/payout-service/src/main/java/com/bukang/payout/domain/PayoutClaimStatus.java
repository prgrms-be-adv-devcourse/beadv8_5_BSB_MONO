package com.bukang.payout.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 청구 상태 (선 환불 후 회수 R3 · R4)
 */
@Getter
@RequiredArgsConstructor
public enum PayoutClaimStatus {
	// 판매자에게 입금을 요청했고 아직 관리자가 입금을 확인하지 않았다
	OPEN("청구 중"),
	// 관리자가 판매자 입금을 확인했다
	CONFIRMED("입금 확인");

	private final String korean;
}
