package com.bukang.payout.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 지급보류 사유 종류 (docs/policy/payout.md 1장 지급보류 2번)
 * 판매자 화면에 사유를 보여 주고, 자동 보류(계좌 미등록)를 사유 종류로 찾기 위해 enum으로 둔다
 */
@Getter
@RequiredArgsConstructor
public enum PayoutHoldReason {
	ACCOUNT_NOT_REGISTERED("정산 계좌 미등록"),
	RACE_CANCELED("대회 취소 처리"),
	// 정산 대사가 정산 쪽이 판매보다 많은 어긋남을 찾았다 (BUK-67). BUK-68 방식대로 한글 상수
	대사_불일치("대사 불일치");

	private final String korean;
}
