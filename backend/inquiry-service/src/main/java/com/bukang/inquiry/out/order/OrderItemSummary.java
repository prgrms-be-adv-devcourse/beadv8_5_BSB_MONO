package com.bukang.inquiry.out.order;

/**
 * 주문 서비스가 돌려주는 주문 항목 하나 (참가권 한 건과 딸린 키트). 계약 필드는 OrderLookupPort 주석을 따른다
 *
 * @param raceId 대회 ID
 * @param raceName 대회 이름
 * @param courseName 코스(종목) 이름 (예: 10K)
 * @param kitSummary 키트 요약 (예: 티셔츠 M, 완주 메달). 키트가 없으면 null
 * @param deliveryStatus 배송 상태. 주문 서비스가 정한 값을 그대로 받아 그대로 보여 준다. 키트가 없으면 null
 */
public record OrderItemSummary(long raceId, String raceName, String courseName, String kitSummary,
	String deliveryStatus) {
}
