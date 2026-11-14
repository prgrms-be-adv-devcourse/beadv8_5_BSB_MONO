package com.bukang.order.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 판매자가 내려받는 목록 종류 (BUK-228)
@Getter
@RequiredArgsConstructor
public enum SellerExportType {
	ROSTER("참가자 명단"),
	KIT_SHIPMENTS("키트 배송 목록");

	private final String label;
}
