package com.bukang.cash.in;

import java.time.LocalDateTime;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.bukang.cash.app.charge.CashChargeReconcileUseCase;
import com.bukang.cash.app.withdrawal.CashWithdrawalReconcileUseCase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * cash.charge.reconcile.interval-millis마다 충전 승인 결과를 끝내 확인 못 한 건을 PG 재조회로 맞춘다
 * fixedDelay라 이전 실행이 끝난 뒤부터 시간을 세므로, 한 번이 오래 걸려도 실행이 겹치지 않는다
 * 테스트에서는 등록하지 않는다. 테스트는 CashChargeReconcileUseCase에 시각을 넣어 직접 부른다
 */
@Slf4j
@Profile("!test")
@Component
@RequiredArgsConstructor
public class CashChargeReconcileScheduler {
	private final CashChargeReconcileUseCase cashChargeReconcileUseCase;
	private final CashWithdrawalReconcileUseCase cashWithdrawalReconcileUseCase;

	@Scheduled(
		fixedDelayString = "${cash.charge.reconcile.interval-millis}",
		initialDelayString = "${cash.charge.reconcile.interval-millis}")
	public void reconcile() {
		// 단계마다 따로 감싸, 충전 재확인이 오류로 끝나도 같은 실행에서 환급 재확인을 한다
		runStep("충전 재확인", () -> cashChargeReconcileUseCase.reconcile(LocalDateTime.now()));
		// 정책: 환급 결제취소 결과를 모르는 몫도 충전 재확인과 같은 주기로 맞춘다
		runStep("환급 재확인", () -> cashWithdrawalReconcileUseCase.reconcile(LocalDateTime.now()));
	}

	private void runStep(String name, Runnable step) {
		try {
			step.run();
		} catch (RuntimeException exception) {
			log.error("{}이 오류로 끝났습니다. 다음 단계는 그대로 돕니다.", name, exception);
		}
	}
}
