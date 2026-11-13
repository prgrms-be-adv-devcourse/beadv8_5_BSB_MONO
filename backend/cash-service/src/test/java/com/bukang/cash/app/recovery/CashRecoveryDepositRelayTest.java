package com.bukang.cash.app.recovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.bukang.cash.domain.exception.recovery.ClaimDepositedSendFailedException;
import com.bukang.cash.domain.recovery.RecoveryDeposit;
import com.bukang.cash.out.kafka.ClaimDepositedSender;
import com.bukang.cash.out.recovery.RecoveryDepositRepository;
import com.bukang.common.shared.cash.event.ClaimDepositedEvent;

// 입금 완료한 회수 입금을 정산에 알리는 릴레이 테스트 (아웃박스 폴링 발행)
// Kafka로 보내는 일은 ClaimDepositedSender가 맡으므로 목으로 바꾸고, 무엇을 보내고 언제 보낸 시각을 채우는지만 본다
@SpringBootTest
@ActiveProfiles("test")
class CashRecoveryDepositRelayTest {
	private static final LocalDateTime DEPOSITED_AT = LocalDateTime.of(2026, 12, 20, 14, 2);
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 12, 20, 14, 10);

	@Autowired
	private CashRecoveryDepositRelayUseCase cashRecoveryDepositRelayUseCase;
	@Autowired
	private RecoveryDepositRepository recoveryDepositRepository;
	@MockitoBean
	private ClaimDepositedSender claimDepositedSender;

	@AfterEach
	void tearDown() {
		recoveryDepositRepository.deleteAll();
	}

	@Test
	@DisplayName("입금 완료이고 아직 안 보낸 회수 입금을 청구 ID·금액·입금 시각으로 보내고, 보낸 시각을 채운다")
	void sendsDepositedClaimAndMarksSent() {
		RecoveryDeposit deposited = saveDeposit("RC-20261214-0A3F", true);

		cashRecoveryDepositRelayUseCase.relay(NOW);

		verify(claimDepositedSender).send(new ClaimDepositedEvent("RC-20261214-0A3F", 400_000, DEPOSITED_AT));
		assertThat(recoveryDepositRepository.findById(deposited.getId()).orElseThrow().getSentAt()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("입금 대기 건과 이미 보낸 건은 보내지 않는다")
	void skipsWaitingAndAlreadySent() {
		saveDeposit("RC-20261214-0A3F", true);
		saveDeposit("RC-20261214-0B00", false);
		cashRecoveryDepositRelayUseCase.relay(NOW);

		cashRecoveryDepositRelayUseCase.relay(NOW.plusMinutes(10));

		verify(claimDepositedSender, times(1)).send(any());
	}

	@Test
	@DisplayName("보내기에 실패하면 오류 없이 이번 실행을 멈추고, 실패한 건과 뒤의 건은 보낸 시각을 비워 둬 다음 실행에서 다시 보낸다")
	void stopsAtSendFailureAndRetriesNextRun() {
		RecoveryDeposit first = saveDeposit("RC-20261214-0A3F", true);
		RecoveryDeposit second = saveDeposit("RC-20261214-0B00", true);
		willThrow(new ClaimDepositedSendFailedException("브로커 응답 없음", null))
			.given(claimDepositedSender).send(any());

		assertThatCode(() -> cashRecoveryDepositRelayUseCase.relay(NOW)).doesNotThrowAnyException();

		verify(claimDepositedSender, times(1)).send(any());
		assertThat(recoveryDepositRepository.findById(first.getId()).orElseThrow().getSentAt()).isNull();
		assertThat(recoveryDepositRepository.findById(second.getId()).orElseThrow().getSentAt()).isNull();
	}

	private RecoveryDeposit saveDeposit(String claimCode, boolean deposited) {
		RecoveryDeposit deposit = new RecoveryDeposit(
			claimCode, 400_000, "국민은행", "X6000123456789", LocalDateTime.of(2027, 3, 14, 10, 0), "ps_secret_1");
		if (deposited) {
			deposit.markDeposited(DEPOSITED_AT);
		}
		return recoveryDepositRepository.save(deposit);
	}
}
