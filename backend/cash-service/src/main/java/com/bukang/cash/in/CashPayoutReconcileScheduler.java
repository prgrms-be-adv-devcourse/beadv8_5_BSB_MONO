package com.bukang.cash.in;

import java.time.LocalDateTime;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import com.bukang.cash.app.payout.CashPayoutReconcileUseCase;

import lombok.RequiredArgsConstructor;

/**
 * 매일 03:30에 결과를 모르는 정산금 송금을 은행에 조회해 맞춘다.
 * 정산 지급 배치(04:00)보다 먼저 돌아, 배치가 같은 정산 ID를 다시 보낼 때 503 대신 완료·실패로 답할 수 있게 한다.
 * 테스트에서는 등록하지 않는다. 테스트는 CashPayoutReconcileUseCase에 시각을 넣어 직접 부른다
 */
@Profile("!test")
@Component
@RequiredArgsConstructor
public class CashPayoutReconcileScheduler {
	// 서버 시간대와 상관없이 한국 시각으로 돈다
	private static final String ZONE = "Asia/Seoul";

	private final CashPayoutReconcileUseCase cashPayoutReconcileUseCase;

	@SchedulerLock(name = "cash-payout-reconcile", lockAtMostFor = "PT30M", lockAtLeastFor = "PT30S")
	@Scheduled(cron = "0 30 3 * * *", zone = ZONE)
	public void reconcile() {
		// 실행 시각만 한국 시각으로 정한다. 기준 시각은 송금 기록의 수정 시각(JPA가 서버 기본 시간대로 찍음)과 비교하므로
		// 같은 시간대여야 한다. 한국 시각을 넣으면 서버가 UTC일 때 9시간 어긋나 방금 보낸 송금도 실패로 확정될 수 있다
		cashPayoutReconcileUseCase.reconcile(LocalDateTime.now());
	}
}
