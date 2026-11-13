package com.bukang.payout.app;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bukang.payout.domain.PayoutBatchTrigger;
import com.bukang.payout.domain.exception.PayoutBatchFutureDateException;
import com.bukang.payout.out.PayoutBatchRunRepository;

/**
 * 관리자 배치 다시 돌리기의 기준일 검사 (BUK-167 리뷰). 미래 기준일로 지급하면 지급일이 안 된 정산이 오늘 나간다
 */
@ExtendWith(MockitoExtension.class)
class PayoutAdminFacadeBatchTest {
	private static final long ADMIN_ID = 99;
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 10, 0);

	@Mock
	private PayoutAdminVerifyUseCase payoutAdminVerifyUseCase;
	@Mock
	private PayoutHoldUseCase payoutHoldUseCase;
	@Mock
	private PayoutClaimUseCase payoutClaimUseCase;
	@Mock
	private PayoutClaimQueryUseCase payoutClaimQueryUseCase;
	@Mock
	private PayoutAdminQueryUseCase payoutAdminQueryUseCase;
	@Mock
	private PayoutBatchUseCase payoutBatchUseCase;
	@Mock
	private PayoutBatchRunRepository payoutBatchRunRepository;
	@InjectMocks
	private PayoutAdminFacade payoutAdminFacade;

	@Test
	@DisplayName("지급 · 생성 배치는 오늘보다 뒤인 기준일을 거절하고 배치를 돌리지 않는다")
	void rejectsFutureDate() {
		LocalDate tomorrow = NOW.toLocalDate().plusDays(1);

		assertThatThrownBy(() -> payoutAdminFacade.runPay(ADMIN_ID, tomorrow, NOW))
			.isInstanceOf(PayoutBatchFutureDateException.class);
		assertThatThrownBy(() -> payoutAdminFacade.runCreate(ADMIN_ID, tomorrow, NOW))
			.isInstanceOf(PayoutBatchFutureDateException.class);
		verify(payoutBatchUseCase, never()).runPay(any(), any(), any(), any());
		verify(payoutBatchUseCase, never()).runCreate(any(), any(), any(), any());
	}

	@Test
	@DisplayName("오늘과 지난날은 받는다. 지난날은 놓친 지급을 따라잡는 데 쓴다")
	void acceptsTodayAndPast() {
		LocalDate today = NOW.toLocalDate();

		payoutAdminFacade.runPay(ADMIN_ID, today, NOW);
		payoutAdminFacade.runPay(ADMIN_ID, today.minusDays(3), NOW);

		verify(payoutBatchUseCase).runPay(PayoutBatchTrigger.관리자, ADMIN_ID, today, NOW);
		verify(payoutBatchUseCase).runPay(eq(PayoutBatchTrigger.관리자), eq(ADMIN_ID), eq(today.minusDays(3)),
			eq(NOW));
	}
}
