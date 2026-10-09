package com.bukang.boundedcontext.payout.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 정산 항목 종류
 * 세미는 정산 대상일 전에 환불된 참가권이 빠지므로 SALE만 생긴다. REFUND는 최종(대상일 ~ 지급 사이 환불)을 위해 둔다
 */
@Getter
@RequiredArgsConstructor
public enum PayoutRecordType {
	SALE("판매"),
	REFUND("환불");

	private final String korean;
}
