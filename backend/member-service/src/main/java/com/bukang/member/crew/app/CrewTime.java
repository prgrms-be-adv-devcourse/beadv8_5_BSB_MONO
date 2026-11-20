package com.bukang.member.crew.app;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 크루 영역의 시간대. 스케줄러의 cron 기준은 한국 시간이고, DB에 적는 시각은 서버 시간대다
 * 시계 빈은 UTC라 LocalDateTime.now(clock)을 쓰면 9시간 어긋난다
 */
public final class CrewTime {
	public static final String ZONE = "Asia/Seoul";

	private CrewTime() {
	}

	// 크루 시각(초대·제한 시작·크루원 가입)을 적고 비교하는 방식. ZONE(서울)이 아니라 서버 시간대로 맞춘다
	public static LocalDateTime toServerLocal(Instant instant) {
		return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
	}
}
