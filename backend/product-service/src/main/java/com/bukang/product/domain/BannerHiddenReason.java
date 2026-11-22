package com.bukang.product.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 배너로 고른 대회가 홈에 보이지 않는 이유 (CRU-147). 관리자 홈 배너 화면에 보여 준다
 * 이유가 여러 개면 Race.bannerHiddenReason이 앞의 것 하나만 고른다 (취소 → 판매 종료 → 판매 중지 → 접수 마감)
 */
@Getter
@RequiredArgsConstructor
public enum BannerHiddenReason {
	CANCELED("대회 취소"),
	CLOSED("판매 종료"),
	STOPPED("판매 중지"),
	APPLICATION_CLOSED("접수 마감");

	// 화면에 쓰는 이름
	private final String label;
}
