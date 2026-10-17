package com.bukang.payout.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 정산 단계 (payout.md 4-3 · 4-4). 대회 하나에 정산 내역이 단계별로 하나씩 생긴다
 * FIRST: 환불 기한 다음 날 만드는 1차 정산금(장마다 가격 × 40%, 수수료 없음)
 * FINAL: 대회 다음 날 만드는 나머지(판매자 정산액 − 1차 정산금 차감)
 */
@Getter
@RequiredArgsConstructor
public enum PayoutStage {
	FIRST("1차"),
	FINAL("최종");

	private final String korean;
}
