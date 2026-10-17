package com.bukang.order.dto;

import lombok.Getter;

/**
 * 1차 정산용 판매 집계: 한 코스에서 판매자의 결제 완료 · 미취소 참가권 가격 합과 장수 (PRO-231)
 * 정산 서비스의 SoldSummary와 필드 이름 · 뜻이 같다. 정산은 가격 합 ÷ 장수를 참가권 1장 가격으로 쓴다
 * priceSum은 판매 내역(SoldEntryDto)의 price와 같은 칸(주문 상품의 실제 결제 금액)을 더한 값이다.
 * 그래서 1차(집계)와 최종(참가권 목록)이 같은 금액에서 출발한다
 */
@Getter
public class SoldSummaryDto {
	private final int sellerId;
	private final int raceId;
	private final int raceCourseId;
	private final long priceSum;
	private final int ticketCount;

	// JPQL 생성자 조회용. sum · count는 Long으로 온다. 한 코스의 장수는 정원을 넘지 않아 int에 들어간다
	public SoldSummaryDto(int sellerId, int raceId, int raceCourseId, Long priceSum, Long ticketCount) {
		this.sellerId = sellerId;
		this.raceId = raceId;
		this.raceCourseId = raceCourseId;
		this.priceSum = priceSum;
		this.ticketCount = Math.toIntExact(ticketCount);
	}
}
