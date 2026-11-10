package com.bukang.cash.app.recovery;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.cash.domain.recovery.RecoveryDeposit;
import com.bukang.cash.domain.recovery.RecoveryDepositStatus;
import com.bukang.cash.out.kafka.ClaimDepositedSender;
import com.bukang.cash.out.recovery.RecoveryDepositRepository;
import com.bukang.common.shared.cash.event.ClaimDepositedEvent;

import lombok.RequiredArgsConstructor;

/**
 * 정산에 "청구 입금됨"을 알린다 (아웃박스 폴링 발행)
 * 입금을 기록한 트랜잭션은 보낸 시각을 비워 두기만 한다. 이 릴레이가 입금 완료인데 보낸 시각이 빈 회수 입금을 찾아 보내고,
 * 브로커가 받았다고 답한 뒤에 보낸 시각을 채운다. 그래서 장부는 남았는데 메시지만 빠지는 일이 없다
 * 보낸 뒤 보낸 시각을 채우기 전에 앱이 죽으면 다음 실행에 한 번 더 보낸다 (최소 한 번 전달). 정산은 이미 확인한 청구를 건너뛴다
 */
@Service
@RequiredArgsConstructor
public class CashRecoveryDepositRelayUseCase {
	private final RecoveryDepositRepository recoveryDepositRepository;
	private final ClaimDepositedSender claimDepositedSender;
	private final TransactionTemplate transactionTemplate;

	public void relay(LocalDateTime now) {
		for (RecoveryDeposit deposit : recoveryDepositRepository
			.findByStatusAndSentAtIsNullOrderByIdAsc(RecoveryDepositStatus.DEPOSITED)) {
			claimDepositedSender.send(
				new ClaimDepositedEvent(deposit.getClaimCode(), deposit.getAmount(), deposit.getDepositedAt()));
			transactionTemplate.executeWithoutResult(status ->
				recoveryDepositRepository.findById(deposit.getId()).orElseThrow().markSent(now));
		}
	}
}
