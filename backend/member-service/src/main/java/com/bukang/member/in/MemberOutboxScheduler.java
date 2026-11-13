package com.bukang.member.in;

import java.time.LocalDateTime;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import com.bukang.member.app.MemberOutboxCleanupUseCase;
import com.bukang.member.app.MemberOutboxRelayUseCase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 회원 아웃박스 발행·정리를 정해진 때에 돌린다 (BUK-41)
 * 테스트에서는 등록하지 않는다. 테스트는 유스케이스에 시각을 넣어 직접 부른다
 */
@Slf4j
@Profile("!test")
@Component
@RequiredArgsConstructor
public class MemberOutboxScheduler {
	private static final String ZONE = "Asia/Seoul";

	private final MemberOutboxRelayUseCase memberOutboxRelayUseCase;
	private final MemberOutboxCleanupUseCase memberOutboxCleanupUseCase;

	// 이전 실행이 끝나고 1초 뒤 다시 보낸다. 메시지는 저장한 뒤 평균 1초 늦게 나간다
	// @SchedulerLock을 붙이지 않는다: 행을 SKIP LOCKED로 읽어 pod 여러 개가 나눠 보낸다 (MemberOutboxRelayUseCase, BUK-206)
	@Scheduled(fixedDelay = 1_000, initialDelay = 5_000)
	public void relay() {
		int sent = memberOutboxRelayUseCase.relay(LocalDateTime.now());
		if (sent > 0) {
			log.debug("아웃박스 메시지를 보냈습니다. 건수={}", sent);
		}
	}

	// 매일 새벽 4시에 보낸 지 7일 지난 행을 지운다
	@SchedulerLock(name = "member-outbox-cleanup", lockAtMostFor = "PT30M", lockAtLeastFor = "PT30S")
	@Scheduled(cron = "0 0 4 * * *", zone = ZONE)
	public void cleanup() {
		int deleted = memberOutboxCleanupUseCase.cleanup(LocalDateTime.now());
		log.info("보낸 지 7일 지난 아웃박스 행을 지웠습니다. 건수={}", deleted);
	}
}
