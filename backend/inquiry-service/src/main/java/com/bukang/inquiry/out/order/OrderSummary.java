package com.bukang.inquiry.out.order;

import java.util.List;

/**
 * 주문 서비스가 돌려주는 주문 요약. 계약 필드는 OrderLookupPort 주석을 따른다
 *
 * @param memberId 주문한 회원 ID (문의를 남긴 회원과 같은지 확인한다)
 * @param orderNo 주문번호 (예: OD-20261110-3F9A)
 * @param paymentAmount 결제 금액(원)
 * @param status 주문 상태. 주문 서비스가 정한 값을 그대로 받아 그대로 보여 준다
 * @param items 주문 항목 목록
 */
public record OrderSummary(long memberId, String orderNo, long paymentAmount, String status,
	List<OrderItemSummary> items) {
	public boolean hasRace(long raceId) {
		return items.stream().anyMatch(item -> item.raceId() == raceId);
	}
}
