package com.bukang.order.dto;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 정산용 판매 내역: 대회 하나의 결제 완료 · 취소 중 참가권을 코스로 묶은 것 (BUK-244)
 * 참가권마다 판매자 · 대회 · 대회일 · 가격을 반복하지 않으려고 묶었다. 정산 어댑터가 풀어서 참가권 목록으로 되돌린다
 * 한 대회의 판매자는 한 명이고, 같은 코스는 가격이 같다(판매 시작 뒤 가격을 바꿀 수 없음)는 정책을 전제로 한다
 * 판매가 없으면 courses는 빈 배열이고 raceDate · sellerId는 null이다
 */
public record SoldEntriesDto(
	@Schema(description = "대회 ID", example = "5")
	long raceId,
	@Schema(description = "주문 때 복사한 대회 날짜. 판매가 없으면 null", example = "2026-11-09")
	LocalDate raceDate,
	@Schema(description = "판매자 회원 ID. 판매가 없으면 null", example = "3")
	Long sellerId,
	@Schema(description = "코스 ID 순 코스 묶음. 판매가 없으면 빈 배열")
	List<Course> courses
) {
	/**
	 * 코스 하나의 판매. ID는 주문 상품 ID 순이다
	 * paidIds는 결제 완료(PAID), blockingIds는 그 밖(지금은 취소 중 CANCELING뿐)이다. 정산은 blockingIds가 있으면 최종 정산을 미룬다
	 */
	public record Course(
		@Schema(description = "코스 ID", example = "11")
		long raceCourseId,
		@Schema(description = "참가권 1장 결제 금액", example = "50000")
		long price,
		@Schema(description = "결제 완료 주문 상품 ID", example = "[1, 2]")
		List<Long> paidIds,
		@Schema(description = "취소 중 주문 상품 ID. 보통 빈 배열", example = "[4]")
		List<Long> blockingIds
	) {
	}
}
