package com.bukang.product.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import com.bukang.common.shared.race.domain.RaceSaleStatus;
import com.bukang.product.domain.Race;
import com.bukang.product.domain.RaceCourse;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 공개 대회 목록의 대회 한 줄. 목록 카드는 대회 정보로 만든 색 포스터를 쓰므로 이미지는 넣지 않는다 (정책 2장 이미지 규격)
@AllArgsConstructor
@Getter
public class RaceSummaryDto {
	private final long raceId;
	private final String title;
	private final String location;
	private final String regionSido;
	private final String regionSigungu;
	private final LocalDateTime raceDate;
	// 판매 상태와 판매 시작일은 판매자 화면용이다
	// 구매자 화면은 상태 대신 크루 예매 기간·개인 예매 오픈·접수 마감 일시로 "오픈 예정·예매 중·마감"을 보여 준다
	private final RaceSaleStatus saleStatus;
	private final LocalDate saleOpenDate;
	// 크루 예매 기간 (크루 예매가 없으면 둘 다 null)
	private final LocalDateTime crewSaleStartAt;
	private final LocalDateTime crewSaleEndAt;
	private final LocalDateTime individualOpenAt;
	private final LocalDateTime applicationCloseAt;
	// 조건에 맞은 종목 중 가장 싼 참가비 ("₩45,000부터"). 조건이 없으면 대회에서 가장 싼 종목의 참가비
	//   "하프"만 골랐으면 5K가 더 싸도 하프 종목의 참가비
	private final long minPrice;
	private final List<RaceSummaryCourseDto> courses;

	public static RaceSummaryDto from(Race race, long minPrice) {
		List<RaceSummaryCourseDto> courses = race.getCourses().stream()
			.sorted(Comparator.comparingLong(RaceCourse::getId))
			.map(RaceSummaryCourseDto::from)
			.toList();
		return new RaceSummaryDto(race.getId(), race.getTitle(), race.getLocation(), race.getRegionSido(),
			race.getRegionSigungu(), race.getRaceDate(), race.getSaleStatus(), race.getSaleOpenDate(),
			race.getCrewSaleStartAt(), race.getCrewSaleEndAt(), race.getIndividualOpenAt(),
			race.getApplicationCloseAt(), minPrice, courses);
	}
}
