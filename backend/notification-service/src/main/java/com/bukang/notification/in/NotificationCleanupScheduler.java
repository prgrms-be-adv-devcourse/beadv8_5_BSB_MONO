package com.bukang.notification.in;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import com.bukang.notification.app.NotificationUseCase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// 매일 새벽 4시(한국 시간)에 7일 지난 알림을 지운다 (11/04 결정). 테스트에서는 돌지 않는다
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class NotificationCleanupScheduler {
	private static final int RETENTION_DAYS = 7;

	private final NotificationUseCase notificationUseCase;

	@SchedulerLock(name = "notification-cleanup", lockAtMostFor = "PT30M", lockAtLeastFor = "PT30S")
	@Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
	public void deleteExpired() {
		int deleted = notificationUseCase.deleteExpired(RETENTION_DAYS);
		log.info("보관 기간 {}일이 지난 알림 {}개를 지웠습니다.", RETENTION_DAYS, deleted);
	}
}
