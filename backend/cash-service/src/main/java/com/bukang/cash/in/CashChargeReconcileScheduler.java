package com.bukang.cash.in;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import com.bukang.cash.app.charge.CashChargeReconcileUseCase;
import com.bukang.cash.app.withdrawal.CashWithdrawalReconcileUseCase;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;

/**
 * cash.charge.reconcile.interval-millis마다 충전 승인 결과를 끝내 확인 못 한 건을 PG 재조회로 맞춘다
 * fixedDelay라 이전 실행이 끝난 뒤부터 시간을 세므로, 한 번이 오래 걸려도 실행이 겹치지 않는다
 * 테스트에서는 등록하지 않는다. 테스트는 CashChargeReconcileUseCase에 시각을 넣어 직접 부른다
 */
@Slf4j
@Profile("!test")
@Component
public class CashChargeReconcileScheduler {
	private static final String CHARGE = "charge";
	private static final String WITHDRAWAL = "withdrawal";

	private final CashChargeReconcileUseCase cashChargeReconcileUseCase;
	private final CashWithdrawalReconcileUseCase cashWithdrawalReconcileUseCase;
	// 단계별 마지막 성공 시각(epoch 초)과 실패 횟수
	private final Map<String, AtomicLong> lastSuccess = new HashMap<>();
	private final Map<String, Counter> failures = new HashMap<>();

	public CashChargeReconcileScheduler(
		CashChargeReconcileUseCase cashChargeReconcileUseCase,
		CashWithdrawalReconcileUseCase cashWithdrawalReconcileUseCase,
		MeterRegistry meterRegistry
	) {
		this.cashChargeReconcileUseCase = cashChargeReconcileUseCase;
		this.cashWithdrawalReconcileUseCase = cashWithdrawalReconcileUseCase;
		// 서버가 뜨자마자 두 단계를 지표로 등록한다. 마지막 성공 시각을 서버 시작 시각에서 출발시켜,
		// 한 번도 성공하지 못해도(매번 실패하거나 스케줄러가 돌지 않아도) 「지난 시간」이 커지는 게 보이게 한다
		long startedAt = Instant.now().getEpochSecond();
		for (String step : List.of(CHARGE, WITHDRAWAL)) {
			lastSuccess.put(step, meterRegistry.gauge(
				"cash.reconcile.last.success", Tags.of("step", step), new AtomicLong(startedAt)));
			failures.put(step, meterRegistry.counter("cash.reconcile.failures", "step", step));
		}
	}

	@SchedulerLock(name = "cash-charge-reconcile", lockAtMostFor = "PT10M")
	@Scheduled(
		fixedDelayString = "${cash.charge.reconcile.interval-millis}",
		initialDelayString = "${cash.charge.reconcile.interval-millis}")
	public void reconcile() {
		// 단계마다 따로 감싸, 충전 재확인이 오류로 끝나도 같은 실행에서 환급 재확인을 한다
		runStep(CHARGE, "충전 재확인", () -> cashChargeReconcileUseCase.reconcile(LocalDateTime.now()));
		// 정책: 환급 결제취소 결과를 모르는 몫도 충전 재확인과 같은 주기로 맞춘다
		runStep(WITHDRAWAL, "환급 재확인", () -> cashWithdrawalReconcileUseCase.reconcile(LocalDateTime.now()));
	}

	// 예외를 삼키면 실행 기록이 늘 성공으로 남아서, 단계별 마지막 성공 시각과 실패 횟수를 따로 지표로 낸다
	private void runStep(String step, String name, Runnable task) {
		try {
			task.run();
			lastSuccess.get(step).set(Instant.now().getEpochSecond());
		} catch (RuntimeException exception) {
			failures.get(step).increment();
			log.error("{}이 오류로 끝났습니다. 다음 단계는 그대로 돕니다.", name, exception);
		}
	}
}
