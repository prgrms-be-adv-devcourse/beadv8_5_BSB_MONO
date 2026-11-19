package com.bukang.inquiry.domain;

/**
 * 문의를 만들 때 받는 값 묶음. Long이 여러 개라 인자 순서를 헷갈리기 쉬워 이름으로 받는다
 * 판매자 문의(SELLER_SUPPORT)는 sellerMemberId·raceId·orderId가 null이다
 */
public record InquiryDraft(long memberId, Long sellerMemberId, Long raceId, Long orderId, InquiryType type,
	String title, String body) {
}
