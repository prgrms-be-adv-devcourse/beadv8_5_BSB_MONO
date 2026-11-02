package com.bukang.product.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.util.StringUtils;

import com.bukang.product.domain.CourseSurface;
import com.bukang.product.domain.CourseType;

import lombok.Builder;
import lombok.Getter;

// 공개 대회 목록 검색 조건. 값이 없는 조건은 거르지 않는다
// 종목 조건(종목 구분·노면·거리·참가비)은 종목 하나라도 맞으면 그 대회가 나온다
@Builder
@Getter
public class RaceSearchCondition {
	// 대회명·장소·종목명·지역·소개에서 찾는다
	private final String keyword;
	// 시·도 (예: 서울특별시). 주소 검색 API가 채운 값과 글자가 같아야 한다
	private final String regionSido;
	// 시·군·구 (예: 성동구). 이름이 겹치는 곳이 있어(중구 등) 시·도와 함께 보낸다
	private final String regionSigungu;
	// 종목 구분. 여러 개면 하나라도 맞는 종목이 있는 대회
	private final List<CourseType> courseTypes;
	private final CourseSurface surface;
	// 거리(km)가 이 값과 같은 종목 (예: 10, 21.1)
	private final BigDecimal distanceKm;
	// 참가비 범위 (둘 다 포함)
	private final Long minPrice;
	private final Long maxPrice;
	// 대회 날짜 범위 (둘 다 포함)
	private final LocalDate raceDateFrom;
	private final LocalDate raceDateTo;
	private final RaceSort sort;

	// 정렬을 고르지 않으면 검색어가 있을 때 정확도순, 없을 때 마감 임박순
	public RaceSort getSort() {
		if (sort != null) {
			return sort;
		}
		return StringUtils.hasText(keyword) ? RaceSort.RELEVANCE : RaceSort.DEADLINE;
	}
}
