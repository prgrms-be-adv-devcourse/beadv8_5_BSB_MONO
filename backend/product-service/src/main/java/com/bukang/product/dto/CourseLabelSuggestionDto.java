package com.bukang.product.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 종목 연관검색어 (예: "2026 JTBC 서울마라톤 하프")
@AllArgsConstructor
@Getter
public class CourseLabelSuggestionDto {
	private final long raceCourseId;
	private final long raceId;
	private final String label;
}
