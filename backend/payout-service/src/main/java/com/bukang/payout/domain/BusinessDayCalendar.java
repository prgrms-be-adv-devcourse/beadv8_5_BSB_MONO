package com.bukang.payout.domain;

import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

/**
 * 영업일 계산을 한곳에 모은다. 세미는 주말만 휴일로 본다
 * 지금은 어디서도 쓰지 않는다. 지급일(매달 20일)과 보류 해제 뒤 지급일(해제일 + 1일) 모두 주말에도 지급한다 (11/2 결정)
 * 휴일 보정을 다시 켤 때 쓰도록 로직과 빈은 남겨 둔다 (10/28과 같은 방식, Payout.payDayOf의 calendar 인자)
 * 최종(PRO-36)에서 공휴일을 DB나 API로 읽게 되면 의존성을 받아야 하므로, static 유틸이 아니라 빈으로 둔다
 */
@Component
public class BusinessDayCalendar {

	// date가 영업일이면 그날, 아니면 다음 영업일 (예: 6/20 토 → 6/22 월)
	public LocalDate nextOrSame(LocalDate date) {
		LocalDate day = date;
		while (isHoliday(day)) {
			day = day.plusDays(1);
		}
		return day;
	}

	private boolean isHoliday(LocalDate date) {
		return date.getDayOfWeek() == SATURDAY || date.getDayOfWeek() == SUNDAY;
	}
}
