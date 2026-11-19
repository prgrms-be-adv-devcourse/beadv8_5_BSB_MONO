package com.bukang.payout.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 판매자가 내려받는 정산 자료 종류 (정산 화면 「엑셀로 내려받기」)
@Getter
@RequiredArgsConstructor
public enum PayoutExportType {
	PAYOUTS("정산 내역"),
	PAYOUT_ITEMS("건별 정산");

	private final String label;
}
