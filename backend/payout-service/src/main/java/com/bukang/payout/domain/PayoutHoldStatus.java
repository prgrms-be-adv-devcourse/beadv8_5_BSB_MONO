package com.bukang.payout.domain;

/**
 * 지급보류 상태 (docs/policy/payout.md 1장 지급보류 4번)
 */
public enum PayoutHoldStatus {
	지급보류,
	해제,
	// 대회 취소로 정산 내역을 닫으면서 시스템이 닫은 보류. 돈이 나가지 않으므로 해제와 나눈다 (PR 569 리뷰)
	취소;
}
