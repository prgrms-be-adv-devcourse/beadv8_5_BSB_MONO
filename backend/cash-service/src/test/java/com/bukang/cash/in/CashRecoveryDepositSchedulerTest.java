package com.bukang.cash.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.TimeZone;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.bukang.cash.app.recovery.CashRecoveryDepositRelayUseCase;
import com.bukang.cash.app.recovery.CashRecoveryDepositUseCase;

// 스케줄러는 test 프로필에서 꺼져 있어 Spring 없이 직접 부른다
class CashRecoveryDepositSchedulerTest {
	@Test
	@DisplayName("서버 기본 시간대가 한국이 아니어도 재확인과 정산 알림에 한국 시각을 넘긴다 (입금 기한은 한국 시간으로 저장한다)")
	void passesKoreaTimeRegardlessOfDefaultTimeZone() {
		CashRecoveryDepositUseCase depositUseCase = mock(CashRecoveryDepositUseCase.class);
		CashRecoveryDepositRelayUseCase relayUseCase = mock(CashRecoveryDepositRelayUseCase.class);
		TimeZone original = TimeZone.getDefault();
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		try {
			new CashRecoveryDepositScheduler(depositUseCase, relayUseCase).reconcile();
		} finally {
			TimeZone.setDefault(original);
		}

		LocalDateTime koreaNow = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
		ArgumentCaptor<LocalDateTime> reconcileNow = ArgumentCaptor.forClass(LocalDateTime.class);
		ArgumentCaptor<LocalDateTime> relayNow = ArgumentCaptor.forClass(LocalDateTime.class);
		verify(depositUseCase).reconcile(reconcileNow.capture());
		verify(relayUseCase).relay(relayNow.capture());
		assertThat(reconcileNow.getValue()).isCloseTo(koreaNow, within(1, ChronoUnit.MINUTES));
		assertThat(relayNow.getValue()).isCloseTo(koreaNow, within(1, ChronoUnit.MINUTES));
	}
}
