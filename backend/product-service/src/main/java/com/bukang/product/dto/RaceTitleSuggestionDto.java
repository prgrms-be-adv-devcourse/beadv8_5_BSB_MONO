package com.bukang.product.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 대회 연관검색어 (누르면 대회 상세로 간다)
@AllArgsConstructor
@Getter
public class RaceTitleSuggestionDto {
	private final long raceId;
	private final String title;
}
