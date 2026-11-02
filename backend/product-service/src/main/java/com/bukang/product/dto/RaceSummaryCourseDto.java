package com.bukang.product.dto;

import java.math.BigDecimal;

import com.bukang.product.domain.CourseStatus;
import com.bukang.product.domain.CourseType;
import com.bukang.product.domain.RaceCourse;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 공개 대회 목록의 종목 요약
@AllArgsConstructor
@Getter
public class RaceSummaryCourseDto {
	private final long raceCourseId;
	private final String name;
	private final CourseType courseType;
	private final BigDecimal distanceKm;
	private final long price;
	private final CourseStatus status;
	// 남은 개인 자리 (세미는 개인 예매만 한다)
	private final int individualRemaining;

	public static RaceSummaryCourseDto from(RaceCourse course) {
		return new RaceSummaryCourseDto(course.getId(), course.getName(), course.getCourseType(),
			course.getDistanceKm(), course.getPrice(), course.getStatus(), course.individualRemaining());
	}
}
