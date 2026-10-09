package com.bukang.boundedcontext.payout.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 정산 내역 상태
 * 세미의 PAID는 실제 송금 없이 상태만 바꾸는 모의 지급이다 (docs/policy/payout.md 1장 지급 5번)
 */
@Getter
@RequiredArgsConstructor
public enum PayoutStatus {
	CALCULATED("정산 예정"),
	HELD("지급보류"),
	PAID("지급 완료");

	private final String korean;
}
