package com.bukang.product.dto;

import java.math.BigDecimal;
import java.util.List;

import com.bukang.product.domain.CourseStatus;
import com.bukang.product.domain.CourseSurface;
import com.bukang.product.domain.CourseType;
import com.bukang.product.domain.RaceCourse;
import com.bukang.product.domain.TshirtSize;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 대회 상세의 종목
@AllArgsConstructor
@Getter
public class RaceDetailCourseDto {
	private final long raceCourseId;
	private final String name;
	private final CourseType courseType;
	private final CourseSurface surface;
	private final BigDecimal distanceKm;
	private final int timeLimitMinutes;
	private final int minAge;
	private final Integer maxAge;
	// 구매자가 내는 참가비. 키트 금액은 이 안에 들어 있다
	private final long price;
	private final boolean kitIncluded;
	// 참가비에 포함된 키트 금액
	private final long kitPrice;
	// 참가 신청 때 고를 수 있는 티셔츠 사이즈 (비어 있으면 티셔츠 없음)
	private final List<TshirtSize> tshirtSizeOption;
	private final CourseStatus status;
	private final int crewQuota;
	private final int individualQuota;
	private final int crewRemaining;
	private final int individualRemaining;

	public static RaceDetailCourseDto from(RaceCourse course) {
		return new RaceDetailCourseDto(course.getId(), course.getName(), course.getCourseType(), course.getSurface(),
			course.getDistanceKm(), course.getTimeLimitMinutes(), course.getMinAge(), course.getMaxAge(),
			course.getPrice(), course.isKitIncluded(), course.getKitPrice(), course.getTshirtSizeOption(),
			course.getStatus(), course.getCrewQuota(), course.getIndividualQuota(), course.crewRemaining(),
			course.individualRemaining());
	}
}
