package com.bukang.cash.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.cash.app.charge.CashChargeReconcileUseCase;
import com.bukang.cash.app.withdrawal.CashWithdrawalReconcileUseCase;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

// 스케줄러는 test 프로필에서 꺼져 있어 Spring 없이 직접 부른다
class CashChargeReconcileSchedulerTest {
	@Test
	@DisplayName("재확인 스케줄러가 돌면 충전 재확인과 함께 환급 재확인도 부른다 (같은 주기)")
	void runsWithdrawalReconcileTogether() {
		CashChargeReconcileUseCase chargeReconcile = mock(CashChargeReconcileUseCase.class);
		CashWithdrawalReconcileUseCase withdrawalReconcile = mock(CashWithdrawalReconcileUseCase.class);

		new CashChargeReconcileScheduler(chargeReconcile, withdrawalReconcile, new SimpleMeterRegistry()).reconcile();

		verify(chargeReconcile).reconcile(any());
		verify(withdrawalReconcile).reconcile(any());
	}

	@Test
	@DisplayName("충전 재확인이 예외로 끝나도 같은 실행에서 환급 재확인을 부른다")
	void withdrawalReconcileRunsEvenIfChargeReconcileFails() {
		CashChargeReconcileUseCase chargeReconcile = mock(CashChargeReconcileUseCase.class);
		CashWithdrawalReconcileUseCase withdrawalReconcile = mock(CashWithdrawalReconcileUseCase.class);
		willThrow(new IllegalStateException("예상 못 한 오류")).given(chargeReconcile).reconcile(any());

		new CashChargeReconcileScheduler(chargeReconcile, withdrawalReconcile, new SimpleMeterRegistry()).reconcile();

		verify(withdrawalReconcile).reconcile(any());
	}

	@Test
	@DisplayName("서버가 뜨면 배치가 한 번도 돌지 않아도 두 단계의 성공 시각(시작 시각)과 실패 횟수(0)가 지표에 있다")
	void registersBothStepsAtStartup() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		long before = Instant.now().getEpochSecond();

		new CashChargeReconcileScheduler(
			mock(CashChargeReconcileUseCase.class), mock(CashWithdrawalReconcileUseCase.class), registry);

		for (String step : new String[] {"charge", "withdrawal"}) {
			assertThat(lastSuccess(registry, step)).isGreaterThanOrEqualTo(before);
			assertThat(registry.get("cash.reconcile.failures").tag("step", step).counter().count()).isZero();
		}
	}

	@Test
	@DisplayName("단계가 실패하면 실패 횟수를 올리고 성공 시각은 그대로 두며, 성공한 단계만 성공 시각을 새로 남긴다")
	void recordsFailureAndLastSuccessPerStep() {
		CashChargeReconcileUseCase chargeReconcile = mock(CashChargeReconcileUseCase.class);
		CashWithdrawalReconcileUseCase withdrawalReconcile = mock(CashWithdrawalReconcileUseCase.class);
		willThrow(new IllegalStateException("DB 오류")).given(chargeReconcile).reconcile(any());
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		CashChargeReconcileScheduler scheduler =
			new CashChargeReconcileScheduler(chargeReconcile, withdrawalReconcile, registry);
		double startedAt = lastSuccess(registry, "charge");

		scheduler.reconcile();

		assertThat(registry.get("cash.reconcile.failures").tag("step", "charge").counter().count()).isEqualTo(1);
		assertThat(registry.get("cash.reconcile.failures").tag("step", "withdrawal").counter().count()).isZero();
		assertThat(lastSuccess(registry, "charge")).isEqualTo(startedAt);
		assertThat(lastSuccess(registry, "withdrawal")).isGreaterThanOrEqualTo(startedAt);
	}

	private static double lastSuccess(SimpleMeterRegistry registry, String step) {
		return registry.get("cash.reconcile.last.success").tag("step", step).gauge().value();
	}
}
