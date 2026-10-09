package com.bukang.payout.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 지급보류 상태 (docs/policy/payout.md 1장 지급보류 4번)
 */
@Getter
@RequiredArgsConstructor
public enum PayoutHoldStatus {
	HELD("지급보류"),
	RELEASED("해제");

	private final String korean;
}
