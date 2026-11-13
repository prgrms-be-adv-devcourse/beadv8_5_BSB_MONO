package com.bukang.member.in;

import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import com.bukang.member.app.RejoinBlockCleanupUseCase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 매일 04:00에 재가입 제한 기간이 지난 탈퇴 회원의 본인인증 해시를 비운다 (BUK-33)
 * test 프로파일에서는 등록하지 않는다. 테스트는 RejoinBlockCleanupUseCase를 직접 부른다
 */
@Slf4j
@Profile("!test")
@Component
@RequiredArgsConstructor
public class RejoinBlockCleanupScheduler {
	private static final String ZONE = "Asia/Seoul";

	private final RejoinBlockCleanupUseCase rejoinBlockCleanupUseCase;

	@SchedulerLock(name = "member-rejoin-block-cleanup", lockAtMostFor = "PT30M", lockAtLeastFor = "PT30S")
	@Scheduled(cron = "0 0 4 * * *", zone = ZONE)
	public void clearExpired() {
		int cleared = rejoinBlockCleanupUseCase.clearExpired(LocalDate.now(ZoneId.of(ZONE)));
		log.info("재가입 제한 기간이 지난 탈퇴 회원 해시 정리: {}건", cleared);
	}
}
