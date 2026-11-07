package com.bukang.payout.in;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.bukang.payout.app.PayoutCreateUseCase;
import com.bukang.payout.app.PayoutReconcileUseCase;

class PayoutCreateSchedulerTest {
	private final PayoutCreateUseCase payoutCreateUseCase = mock(PayoutCreateUseCase.class);
	private final PayoutReconcileUseCase payoutReconcileUseCase = mock(PayoutReconcileUseCase.class);
	private final PayoutCreateScheduler scheduler = new PayoutCreateScheduler(payoutCreateUseCase,
		payoutReconcileUseCase);

	@Test
	@DisplayName("정산 생성 배치 뒤에 같은 실행 기준일 · 시작 시각으로 대사를 한 번 돌린다")
	void reconcilesAfterCreate() {
		scheduler.createPayouts();

		InOrder order = inOrder(payoutCreateUseCase, payoutReconcileUseCase);
		order.verify(payoutCreateUseCase).run(any(), any());
		order.verify(payoutReconcileUseCase).run(any(), any());
	}

	@Test
	@DisplayName("AC-67-10 대사가 예외를 던져도 스케줄러 밖으로 나가지 않는다 (정산 생성은 이미 끝났다)")
	void reconcileFailureDoesNotEscape() {
		doThrow(new IllegalStateException("DB 연결 끊김")).when(payoutReconcileUseCase).run(any(), any());

		assertThatCode(scheduler::createPayouts).doesNotThrowAnyException();
	}
}
