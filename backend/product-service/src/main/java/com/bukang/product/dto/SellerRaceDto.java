package com.bukang.product.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import com.bukang.common.shared.race.domain.RaceSaleStatus;
import com.bukang.product.domain.ProductSeller;
import com.bukang.product.domain.Race;
import com.bukang.product.domain.RaceCourse;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 판매자·관리자 대회 관리의 대회 한 줄 (취소된 대회도 나온다)
@AllArgsConstructor
@Getter
public class SellerRaceDto {
	private final long raceId;
	private final String title;
	private final LocalDateTime raceDate;
	private final RaceSaleStatus saleStatus;
	// 이날 0시부터 판매 중으로 표시되고, 판매 시작 뒤 수정 규칙이 걸린다
	private final LocalDate saleOpenDate;
	private final LocalDateTime applicationCloseAt;
	// 판매자 사업자 이름 (관리자 화면이 판매자를 보여 줄 때 쓴다, CRU-44)
	private final String sellerName;
	// 크루 예매 시작 (크루 예매가 없으면 null)과 개인 예매 오픈. 접수 기간 시작을 보여 줄 때 쓴다
	private final LocalDateTime crewSaleStartAt;
	private final LocalDateTime individualOpenAt;
	// 환불 기준일: 이날까지 취소하면 전액 환불
	private final LocalDate refundDeadline;
	private final List<SellerRaceCourseDto> courses;

	public static SellerRaceDto from(Race race) {
		List<SellerRaceCourseDto> courses = race.getCourses().stream()
			.sorted(Comparator.comparingLong(RaceCourse::getId))
			.map(SellerRaceCourseDto::from)
			.toList();
		return new SellerRaceDto(race.getId(), race.getTitle(), race.getRaceDate(), race.getSaleStatus(),
			race.getSaleOpenDate(), race.getApplicationCloseAt(), sellerName(race.getSeller()),
			race.getCrewSaleStartAt(), race.getIndividualOpenAt(), race.getRefundDeadline(), courses);
	}

	// 상호명은 사업자만 있어, 비사업자는 성명을 판매자 이름으로 쓴다
	private static String sellerName(ProductSeller seller) {
		return seller.getBusinessName() != null ? seller.getBusinessName() : seller.getRepresentativeName();
	}
}
