package com.bukang.queue.dto;

import java.time.LocalDate;

import com.bukang.queue.domain.QueueEntryType;
import com.bukang.queue.domain.QueueRace;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 대회에 들어갈 대기열. 대회 상세의 [예매하기] 버튼이 어느 대기열로 갈지 정할 때 쓴다
@AllArgsConstructor
@Getter
public class QueueEntryResponseDto {
	private final long raceId;
	// PARTY(파티 대기열) 또는 INDIVIDUAL(개인 대기열)
	private final QueueEntryType entryType;
	// 그 대기열이 열리는 날짜와 마지막 날짜 (파티: 크루 예매 시작 ~ 크루 마지막 날, 개인: 개인 예매 오픈 ~ 접수 마감)
	private final LocalDate openDate;
	private final LocalDate closeDate;
	// 그 대기열에 등록하는 API 주소 (POST)
	private final String url;

	public static QueueEntryResponseDto of(QueueRace race, LocalDate today) {
		QueueEntryType entryType = race.entryTypeOn(today);
		return new QueueEntryResponseDto(race.getId(), entryType, race.openDate(entryType),
			race.closeDate(entryType), entryType.getRegisterUrl());
	}
}
