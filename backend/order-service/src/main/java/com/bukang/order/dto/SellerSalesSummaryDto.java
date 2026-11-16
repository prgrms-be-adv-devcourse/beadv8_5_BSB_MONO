package com.bukang.order.dto;

import lombok.Getter;

/**
 * 판매자의 기간 판매·취소 합계 (BUK-247, 취소 BUK-264)
 * 판매: 결제일이 기간 안인 결제 완료·취소 중 참가권의 장수와 결제 금액 합
 * 취소: 취소를 마친 시각이 기간 안인 취소된 참가권의 장수와 환불액 합
 * 판매자 AI 「이번 달 판매액」이 쓴다
 */
@Getter
public class SellerSalesSummaryDto {
	private final long soldCount;
	private final long soldAmount;
	private final long canceledCount;
	private final long refundedAmount;

	// JPQL 생성자 조회용. sum은 Long으로 오고, 고른 줄이 없으면 null이다
	public SellerSalesSummaryDto(Long soldCount, Long soldAmount, Long canceledCount, Long refundedAmount) {
		this.soldCount = orZero(soldCount);
		this.soldAmount = orZero(soldAmount);
		this.canceledCount = orZero(canceledCount);
		this.refundedAmount = orZero(refundedAmount);
	}

	private static long orZero(Long value) {
		return value == null ? 0 : value;
	}
}
