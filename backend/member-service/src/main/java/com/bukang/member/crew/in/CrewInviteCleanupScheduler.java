package com.bukang.member.crew.in;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import com.bukang.member.crew.app.CrewInviteCleanupUseCase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 매일 04:30에 만료·취소된 지 7일 지난 크루 초대를 지운다 (BUK-100)
 * test 프로파일에서는 등록하지 않는다. 테스트는 CrewInviteCleanupUseCase에 시각을 넣어 직접 부른다
 */
@Slf4j
@Profile("!test")
@Component
@RequiredArgsConstructor
public class CrewInviteCleanupScheduler {
	private static final String ZONE = "Asia/Seoul";

	private final CrewInviteCleanupUseCase crewInviteCleanupUseCase;
	// 초대 시각을 적는 CrewFacade.now()와 같은 방식(서버 시간대)으로 읽는다
	// 시계 빈은 UTC라 LocalDateTime.now(clock)을 쓰면 9시간 어긋난다
	private final Clock clock;

	@SchedulerLock(name = "member-crew-invite-cleanup", lockAtMostFor = "PT30M", lockAtLeastFor = "PT30S")
	@Scheduled(cron = "0 30 4 * * *", zone = ZONE)
	public void cleanup() {
		LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault());
		int deleted = crewInviteCleanupUseCase.cleanup(now);
		log.info("만료·취소된 지 7일 지난 크루 초대를 지웠습니다. 건수={}", deleted);
	}
}
