package com.bukang.cash.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import com.bukang.cash.app.payout.CashPayoutReconcileUseCase;

// 스케줄러는 test 프로필에서 꺼져 있어 Spring 없이 직접 부른다
class CashPayoutReconcileSchedulerTest {
	@Test
	@DisplayName("정산 지급 배치(04:00)보다 먼저, 매일 03:30(한국 시각)에 정산금 송금 재확인을 부른다")
	void reconcilesDailyAt0330() throws NoSuchMethodException {
		CashPayoutReconcileUseCase reconcile = mock(CashPayoutReconcileUseCase.class);

		new CashPayoutReconcileScheduler(reconcile).reconcile();

		verify(reconcile).reconcile(any());
		Scheduled scheduled =
			CashPayoutReconcileScheduler.class.getMethod("reconcile").getAnnotation(Scheduled.class);
		assertThat(scheduled).isNotNull();
		assertThat(scheduled.cron()).isEqualTo("0 30 3 * * *");
		assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
	}
}
