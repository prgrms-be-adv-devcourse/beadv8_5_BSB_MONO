package com.bukang.cash.in;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.bukang.cash.app.recovery.CashRecoveryDepositUseCase;

import lombok.RequiredArgsConstructor;

/**
 * cash.recovery.reconcile.interval-millis마다 입금 알림이 빠진 회수 입금을 PG 재조회로 맞춘다
 * fixedDelay라 이전 실행이 끝난 뒤부터 시간을 세므로 실행이 겹치지 않는다
 * 테스트에서는 등록하지 않는다. 테스트는 CashRecoveryDepositUseCase를 직접 부른다
 */
@Profile("!test")
@Component
@RequiredArgsConstructor
public class CashRecoveryDepositScheduler {
	private final CashRecoveryDepositUseCase cashRecoveryDepositUseCase;

	@Scheduled(
		fixedDelayString = "${cash.recovery.reconcile.interval-millis}",
		initialDelayString = "${cash.recovery.reconcile.interval-millis}")
	public void reconcile() {
		cashRecoveryDepositUseCase.reconcile();
	}
}
