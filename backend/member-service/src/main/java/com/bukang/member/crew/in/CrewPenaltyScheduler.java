package com.bukang.member.crew.in;

import java.time.Clock;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.bukang.member.crew.app.CrewPenaltyRecordUseCase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 매일 04:30에 결제율 미달 크루의 파티 만들기 제한을 저장한다 (BUK-187)
 * test 프로파일에서는 등록하지 않는다. 테스트는 CrewPenaltyRecordUseCase에 시각을 넣어 직접 부른다
 */
@Slf4j
@Profile("!test")
@Component
@RequiredArgsConstructor
public class CrewPenaltyScheduler {
	private static final String ZONE = "Asia/Seoul";

	private final CrewPenaltyRecordUseCase crewPenaltyRecordUseCase;
	private final Clock clock;

	@Scheduled(cron = "0 30 4 * * *", zone = ZONE)
	public void record() {
		int saved = crewPenaltyRecordUseCase.record(clock.instant());
		log.info("결제율 미달 크루의 파티 만들기 제한을 저장했습니다. 새로 저장한 건수={}", saved);
	}
}
