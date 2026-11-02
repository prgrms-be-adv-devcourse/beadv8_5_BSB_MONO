package com.bukang.product.dto;

import java.time.LocalDate;
import java.util.List;

import com.bukang.common.shared.race.domain.RaceSaleStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class RaceSaveDto {
	private final long raceId;
	private final RaceSaleStatus saleStatus;
	// 판매 시작일: 크루 예매 시작·개인 예매 오픈 중 이른 날짜 (서버가 채운다). 이날 0시부터 판매 중으로 표시된다
	private final LocalDate saleOpenDate;
	// 요청한 종목 순서대로. 수정할 때 각 종목의 courseId로 보낸다
	private final List<Long> courseIds;
}
