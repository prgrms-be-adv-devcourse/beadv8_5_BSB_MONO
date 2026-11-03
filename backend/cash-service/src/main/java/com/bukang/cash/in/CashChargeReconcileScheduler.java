package com.bukang.cash.in;

import java.time.LocalDateTime;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.bukang.cash.app.CashChargeReconcileUseCase;

import lombok.RequiredArgsConstructor;

/**
 * 1분마다 충전 승인 결과를 끝내 확인 못 한 건을 PG 재조회로 맞춘다
 * fixedDelay라 이전 실행이 끝난 뒤 1분을 세므로, 한 번이 오래 걸려도 실행이 겹치지 않는다
 * 테스트에서는 등록하지 않는다. 테스트는 CashChargeReconcileUseCase에 시각을 넣어 직접 부른다
 */
@Profile("!test")
@Component
@RequiredArgsConstructor
public class CashChargeReconcileScheduler {
	private final CashChargeReconcileUseCase cashChargeReconcileUseCase;

	@Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
	public void reconcile() {
		cashChargeReconcileUseCase.reconcile(LocalDateTime.now());
	}
}
