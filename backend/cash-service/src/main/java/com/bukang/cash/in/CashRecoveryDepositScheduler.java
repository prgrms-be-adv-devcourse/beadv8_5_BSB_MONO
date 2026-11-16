package com.bukang.cash.in;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import com.bukang.cash.app.recovery.CashRecoveryDepositRelayUseCase;
import com.bukang.cash.app.recovery.CashRecoveryDepositUseCase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * cash.recovery.reconcile.interval-millis마다 입금 알림이 빠진 회수 입금을 PG 재조회로 맞추고, 입금 완료를 정산에 알린다
 * fixedDelay라 이전 실행이 끝난 뒤부터 시간을 세므로 실행이 겹치지 않는다
 * 테스트에서는 등록하지 않는다. 테스트는 CashRecoveryDepositUseCase를 직접 부른다
 */
@Slf4j
@Profile("!test")
@Component
@RequiredArgsConstructor
public class CashRecoveryDepositScheduler {
	// 입금 기한은 한국 시간으로 저장하므로, 서버 기본 시간대와 상관없이 한국 시각으로 비교한다
	private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

	private final CashRecoveryDepositUseCase cashRecoveryDepositUseCase;
	private final CashRecoveryDepositRelayUseCase cashRecoveryDepositRelayUseCase;

	@SchedulerLock(name = "cash-recovery-deposit", lockAtMostFor = "PT30M")
	@Scheduled(
		fixedDelayString = "${cash.recovery.reconcile.interval-millis}",
		initialDelayString = "${cash.recovery.reconcile.interval-millis}")
	public void reconcile() {
		LocalDateTime now = LocalDateTime.now(KOREA);
		// 단계마다 따로 감싸, 재확인이 오류로 끝나도 이미 입금 완료로 기록한 건은 정산에 알린다
		runStep("회수 입금 재확인", () -> cashRecoveryDepositUseCase.reconcile(now));
		// 재확인에서 새로 입금 완료가 된 건도 이번 실행에서 바로 정산에 알린다
		runStep("청구 입금 정산 알림", () -> cashRecoveryDepositRelayUseCase.relay(now));
	}

	private void runStep(String name, Runnable step) {
		try {
			step.run();
		} catch (RuntimeException exception) {
			log.error("{}이 오류로 끝났습니다. 다음 단계는 그대로 돕니다.", name, exception);
		}
	}
}
