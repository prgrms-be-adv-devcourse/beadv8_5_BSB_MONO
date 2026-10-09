package com.bukang.payout.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 정산 항목 종류
 * 세미는 SALE만 생긴다: 대상일 전에 환불된 참가권은 빠지고, 대상일 후 환불은 막는다(PRO-28).
 * REFUND는 최종의 '대상일 ~ 지급 사이 환불'을 음수 금액으로 기록하기 위해 둔다(PRO-32). 지급 뒤 환불은 항목이 아니라 회수(PRO-33)다
 */
@Getter
@RequiredArgsConstructor
public enum PayoutRecordType {
	SALE("판매"),
	REFUND("환불");

	private final String korean;
}
