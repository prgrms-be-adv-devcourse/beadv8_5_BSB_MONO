package com.bukang.queue.in;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import com.bukang.queue.app.QueueFacade;

import lombok.RequiredArgsConstructor;

/**
 * 대기열(대회·종목)마다 끝 시각이 지난 active를 지우고, waiting 앞에서부터 정해진 인원을 active로 승격한다
 * 한 주기에 개인 줄과 파티 줄을 모두 승격한다 (QueuePromoteUseCase)
 * 주기는 queue.schedule.time 하나이고, 인원·활성 시간은 개인 줄이 queue.*, 파티 줄이 queue.party.*에 있다
 * 테스트에서는 등록하지 않는다. 테스트는 QueuePromoteUseCase에 시각을 넣어 직접 부른다
 */
@Profile("!test")
@Component
@RequiredArgsConstructor
public class QueuePromotionScheduler {
	private final QueueFacade queueFacade;

	// 앞 실행이 끝난 뒤 queue.schedule.time(ms)만큼 쉬고 다시 돈다
	@SchedulerLock(name = "queue-promotion", lockAtMostFor = "PT1M")
	@Scheduled(fixedDelayString = "${queue.schedule.time}", initialDelayString = "${queue.schedule.time}")
	public void promote() {
		queueFacade.promote();
	}
}
