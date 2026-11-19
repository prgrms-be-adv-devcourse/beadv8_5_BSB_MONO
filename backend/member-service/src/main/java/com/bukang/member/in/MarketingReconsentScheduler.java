package com.bukang.member.in;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import com.bukang.member.app.MarketingReconsentUseCase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 매일 10:00(낮 시간)에 마케팅 동의 2년이 된 회원의 재동의 알림을 요청한다 (CRU-22)
 * test 프로파일에서는 등록하지 않는다. 테스트는 MarketingReconsentUseCase에 시각을 넣어 직접 부른다
 */
@Slf4j
@Profile("!test")
@Component
@RequiredArgsConstructor
public class MarketingReconsentScheduler {
	private static final String ZONE = "Asia/Seoul";

	private final MarketingReconsentUseCase marketingReconsentUseCase;

	@SchedulerLock(name = "member-marketing-reconsent", lockAtMostFor = "PT30M", lockAtLeastFor = "PT30S")
	@Scheduled(cron = "0 0 10 * * *", zone = ZONE)
	public void notifyDue() {
		int notified = marketingReconsentUseCase.notifyDue(LocalDateTime.now(ZoneId.of(ZONE)));
		log.info("마케팅 재동의 알림 요청: {}건", notified);
	}
}
