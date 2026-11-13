package com.bukang.payout.in;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.payout.app.PayoutBatchUseCase;
import com.bukang.payout.domain.PayoutBatchTrigger;
import com.bukang.payout.domain.exception.PayoutBatchRunningException;

/**
 * 생성 · 지급 스케줄러는 잠금 · 실행 기록을 맡는 PayoutBatchUseCase를 스케줄 계기로 부른다 (BUK-167)
 * 대사 순서 · 대사 실패 처리는 PayoutBatchUseCaseTest가 본다
 */
class PayoutCreateSchedulerTest {
	private final PayoutBatchUseCase payoutBatchUseCase = mock(PayoutBatchUseCase.class);
	private final PayoutCreateScheduler createScheduler = new PayoutCreateScheduler(payoutBatchUseCase);
	private final PayoutPayScheduler payScheduler = new PayoutPayScheduler(payoutBatchUseCase);

	@Test
	@DisplayName("생성 · 지급 스케줄러는 스케줄 계기로, 시작한 사람 없이 배치를 돌린다")
	void runsWithScheduleTrigger() {
		createScheduler.createPayouts();
		payScheduler.payPayouts();

		verify(payoutBatchUseCase).runCreate(eq(PayoutBatchTrigger.스케줄), isNull(), any(), any());
		verify(payoutBatchUseCase).runPay(eq(PayoutBatchTrigger.스케줄), isNull(), any(), any());
	}

	@Test
	@DisplayName("BUK-167 다른 서버나 관리자가 이미 돌리고 있으면 예외를 밖으로 내지 않고 이번 실행을 건너뛴다")
	void skipsWhenAlreadyRunning() {
		doThrow(new PayoutBatchRunningException("정산 생성")).when(payoutBatchUseCase)
			.runCreate(any(), any(), any(), any());
		doThrow(new PayoutBatchRunningException("정산 지급")).when(payoutBatchUseCase)
			.runPay(any(), any(), any(), any());

		assertThatCode(createScheduler::createPayouts).doesNotThrowAnyException();
		assertThatCode(payScheduler::payPayouts).doesNotThrowAnyException();
	}
}
