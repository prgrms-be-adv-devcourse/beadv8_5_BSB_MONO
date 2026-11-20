package com.bukang.order.dto;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

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
	 * format이 BITSET · ROARING이면 결제 완료 ID를 paidIds 대신 paidFormat · paidBase · paidCount · paidBitmap으로
	 * 보낸다 (SoldEntriesFormat)
	 * 받는 쪽은 paidFormat을 보고 푼다. ID 범위가 int를 넘는 코스는 비트맵을 요청해도 paidIds로 온다
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Course(
		@Schema(description = "코스 ID", example = "11")
		long raceCourseId,
		@Schema(description = "참가권 1장 결제 금액", example = "50000")
		long price,
		@Schema(description = "결제 완료 주문 상품 ID. format이 IDS일 때만 있음", example = "[1, 2]")
		List<Long> paidIds,
		@Schema(description = "취소 중 주문 상품 ID. 보통 빈 배열", example = "[4]")
		List<Long> blockingIds,
		@Schema(description = "paidBitmap의 형식(BITSET · ROARING). 비트맵으로 보낼 때만 있음", example = "ROARING")
		SoldEntriesFormat paidFormat,
		@Schema(description = "비트맵 위치 0에 해당하는 주문 상품 ID(가장 작은 결제 완료 ID). BITSET · ROARING일 때만 있음",
			example = "1")
		Long paidBase,
		@Schema(description = "결제 완료 참가권 수. BITSET · ROARING일 때만 있음", example = "2")
		Integer paidCount,
		@Schema(description = "결제 완료 ID 비트맵(base64). BITSET은 BitSet.toByteArray, ROARING은 RoaringBitmap 직렬화",
			example = "Aw==")
		String paidBitmap
	) {
		public Course(long raceCourseId, long price, List<Long> paidIds, List<Long> blockingIds) {
			this(raceCourseId, price, paidIds, blockingIds, null, null, null, null);
		}

		public static Course bitmap(long raceCourseId, long price, SoldEntriesFormat paidFormat, long paidBase,
			int paidCount, String paidBitmap, List<Long> blockingIds) {
			return new Course(raceCourseId, price, null, blockingIds, paidFormat, paidBase, paidCount, paidBitmap);
		}
	}
}
