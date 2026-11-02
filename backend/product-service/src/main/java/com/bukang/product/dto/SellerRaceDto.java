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

// 판매자 대회 관리의 대회 한 줄 (취소된 대회도 나온다)
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
	private final List<SellerRaceCourseDto> courses;

	public static SellerRaceDto from(Race race) {
		List<SellerRaceCourseDto> courses = race.getCourses().stream()
			.sorted(Comparator.comparingLong(RaceCourse::getId))
			.map(SellerRaceCourseDto::from)
			.toList();
		return new SellerRaceDto(race.getId(), race.getTitle(), race.getRaceDate(), race.getSaleStatus(),
			race.getSaleOpenDate(), race.getApplicationCloseAt(), courses);
	}
}
