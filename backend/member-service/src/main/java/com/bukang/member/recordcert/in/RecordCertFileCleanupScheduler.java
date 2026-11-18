package com.bukang.member.recordcert.in;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import com.bukang.member.recordcert.app.RecordCertFileCleanupUseCase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 매일 04:40에 끝난 지 30일 지난 제출의 기록증 사진을 지운다 (BUK-278)
 * test 프로파일에서는 등록하지 않는다. 테스트는 RecordCertFileCleanupUseCase에 시각을 넣어 직접 부른다
 */
@Slf4j
@Profile("!test")
@Component
@RequiredArgsConstructor
public class RecordCertFileCleanupScheduler {
	private static final String ZONE = "Asia/Seoul";

	private final RecordCertFileCleanupUseCase recordCertFileCleanupUseCase;
	// 제출 시각을 적는 방식(서버 시간대)과 같게 읽는다. 시계 빈은 UTC라 LocalDateTime.now(clock)을 쓰면 9시간 어긋난다
	private final Clock clock;

	@SchedulerLock(name = "member-record-cert-file-cleanup", lockAtMostFor = "PT30M", lockAtLeastFor = "PT30S")
	@Scheduled(cron = "0 40 4 * * *", zone = ZONE)
	public void cleanup() {
		LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault());
		int deleted = recordCertFileCleanupUseCase.cleanup(now);
		log.info("끝난 지 30일 지난 제출의 기록증 사진을 지웠습니다. 건수={}", deleted);
	}
}
