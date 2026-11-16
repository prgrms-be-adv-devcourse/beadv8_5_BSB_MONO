package com.bukang.order.dto;

import lombok.Getter;

/**
 * 판매자의 기간 판매 합계 (BUK-247). 결제일이 기간 안인 결제 완료·취소 중 참가권의 장수와 결제 금액 합
 * 판매자 AI 「이번 달 판매액」과 대시보드 판매 숫자가 쓴다
 */
@Getter
public class SellerSalesSummaryDto {
	private final long soldCount;
	private final long soldAmount;

	// JPQL 생성자 조회용. count·sum은 Long으로 오고, 고른 줄이 없으면 sum은 null이다
	public SellerSalesSummaryDto(Long soldCount, Long soldAmount) {
		this.soldCount = soldCount == null ? 0 : soldCount;
		this.soldAmount = soldAmount == null ? 0 : soldAmount;
	}
}
