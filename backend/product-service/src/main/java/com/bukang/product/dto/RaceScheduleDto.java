package com.bukang.product.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.bukang.product.domain.Race;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 정산 서비스에 주는 대회 일정 (내부 API, PRO-230). 정산 서비스 RaceSchedule과 필드 이름·뜻이 같다
 * raceName은 대회명(race.title)이고, 정산은 영수증처럼 정산 내역에 저장한다
 * raceDate는 저장한 대회 일시 그대로 준다. 정산은 날짜만 쓴다
 */
@AllArgsConstructor
@Getter
public class RaceScheduleDto {
	private final int raceId;
	// 대회명 (race.title)
	private final String raceName;
	private final LocalDateTime raceDate;
	private final LocalDate refundDeadline;

	public static RaceScheduleDto from(Race race) {
		return new RaceScheduleDto(race.getId(), race.getTitle(), race.getRaceDate(), race.getRefundDeadline());
	}
}
