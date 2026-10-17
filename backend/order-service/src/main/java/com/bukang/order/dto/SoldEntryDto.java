package com.bukang.order.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;

/**
 * 정산할 참가권 1장 (결제되고 취소되지 않은 주문 상품). 정산 서비스의 SoldEntry와 필드 이름·뜻이 같다
 * 참가권은 수량 1이라 price는 참가권 1장 값(실제 결제 금액, 키트 포함)이다
 * raceDate는 주문 때 복사한 대회 날짜다. 정산은 이 날짜로 정산 대상일과 정산 월을 정한다
 * raceCourseId는 참가권의 코스다. 정산은 원장 판매 · 수수료 줄에 적어 판매자가 코스별로 본다 (PRO-231)
 */
@Getter
public class SoldEntryDto {
	private final int orderItemId;
	private final int sellerId;
	private final int raceId;
	private final int raceCourseId;
	private final LocalDate raceDate;
	private final long price;

	// JPQL 생성자 조회용. 주문 상품의 대회 일시(LocalDateTime)를 날짜로 바꿔 내려준다
	public SoldEntryDto(int orderItemId, int sellerId, int raceId, int raceCourseId, LocalDateTime raceDateTime,
		long price) {
		this.orderItemId = orderItemId;
		this.sellerId = sellerId;
		this.raceId = raceId;
		this.raceCourseId = raceCourseId;
		this.raceDate = raceDateTime.toLocalDate();
		this.price = price;
	}
}
