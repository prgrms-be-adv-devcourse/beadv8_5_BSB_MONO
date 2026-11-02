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

// 대회 상세. 종목 이미지는 file-service에서 종목마다 따로 받는다
// (GET /api/v1/file/files?refType=RaceCourse&refId={raceCourseId}, 이미지 URL은 30분 뒤 만료)
@AllArgsConstructor
@Getter
public class RaceDetailDto {
	private final long raceId;
	private final String title;
	private final String description;
	private final String location;
	private final String regionSido;
	private final String regionSigungu;
	private final LocalDateTime raceDate;
	// 판매 상태와 판매 시작일은 판매자 화면용이다. 구매자 화면은 크루 예매 기간·개인 예매 오픈·접수 마감 일시를 쓴다
	private final RaceSaleStatus saleStatus;
	private final LocalDate saleOpenDate;
	private final LocalDateTime crewSaleStartAt;
	private final LocalDateTime crewSaleEndAt;
	private final LocalDateTime individualOpenAt;
	// 접수 마감 = 참가자 정보 수정 기한
	private final LocalDateTime applicationCloseAt;
	// 이날까지 취소하면 전액 환불
	private final LocalDate refundDeadline;
	private final String refundPolicy;
	private final int crewMinCount;
	private final Integer crewMaxCount;
	// 판매 시작 뒤 대회 일시·장소를 바꾼 일시. 이날부터 7일 동안 전액 환불 (바뀐 적 없으면 null)
	private final LocalDateTime scheduleChangedAt;
	private final RaceSellerDto seller;
	private final List<RaceDetailCourseDto> courses;

	public static RaceDetailDto from(Race race) {
		List<RaceDetailCourseDto> courses = race.getCourses().stream()
			.sorted(Comparator.comparingLong(RaceCourse::getId))
			.map(RaceDetailCourseDto::from)
			.toList();
		return new RaceDetailDto(race.getId(), race.getTitle(), race.getDescription(), race.getLocation(),
			race.getRegionSido(), race.getRegionSigungu(), race.getRaceDate(), race.getSaleStatus(),
			race.getSaleOpenDate(), race.getCrewSaleStartAt(), race.getCrewSaleEndAt(), race.getIndividualOpenAt(),
			race.getApplicationCloseAt(), race.getRefundDeadline(), race.getRefundPolicy(), race.getCrewMinCount(),
			race.getCrewMaxCount(), race.getScheduleChangedAt(), RaceSellerDto.from(race.getSeller()), courses);
	}
}
