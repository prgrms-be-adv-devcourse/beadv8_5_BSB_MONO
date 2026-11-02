package com.bukang.product.dto;

import com.bukang.product.domain.CourseStatus;
import com.bukang.product.domain.RaceCourse;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 판매자 대회 관리의 종목 판매 현황 ("판매 / 정원")
@AllArgsConstructor
@Getter
public class SellerRaceCourseDto {
	private final long raceCourseId;
	private final String name;
	private final long price;
	private final CourseStatus status;
	// 크루 몫 + 개인 몫
	private final int quota;
	// 결제 대기 중인 자리 (크루 + 개인)
	private final int reserved;
	// 판매된 자리 (크루 + 개인)
	private final int sold;

	public static SellerRaceCourseDto from(RaceCourse course) {
		return new SellerRaceCourseDto(course.getId(), course.getName(), course.getPrice(), course.getStatus(),
			course.getCrewQuota() + course.getIndividualQuota(),
			course.getCrewReserved() + course.getIndividualReserved(),
			course.getCrewSold() + course.getIndividualSold());
	}
}
