package com.bukang.payout.app;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.common.shared.kafka.dlt.DltKafkaEventDto;
import com.bukang.common.shared.kafka.dlt.DltKafkaEventStatus;
import com.bukang.common.shared.kafka.dlt.DltKafkaResendException;
import com.bukang.common.shared.kafka.dlt.KafkaDltMessage;
import com.bukang.payout.domain.PayoutDltKafkaEvent;

import lombok.RequiredArgsConstructor;

/**
 * 정산 DLT 실패 테이블(PAYOUT_DLT_KAFKA_EVENT)의 트랜잭션을 연다. 일은 PayoutDltUseCase가 한다 (ProductFacade와 같은 나눔)
 */
@Service
@RequiredArgsConstructor
public class PayoutDltFacade {
	// 다른 정산 진입점과 같이 한국 시간으로 남긴다
	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	private final PayoutDltUseCase payoutDltUseCase;
	private final TransactionTemplate transactionTemplate;

	// 재시도를 모두 실패해 DLT로 온 메시지를 남기고, 커밋 뒤 Slack으로 알린다 (PayoutKafkaDltConsumer가 부른다)
	@Transactional
	public void recordDlt(KafkaDltMessage message) {
		payoutDltUseCase.record(message);
	}

	// 본 리스너가 메시지를 처리한 뒤 부른다. 다시 보낸 메시지였으면 처리 완료로 닫는다
	// 본 처리와 트랜잭션이 다르다. 여기서 실패해도 본 처리는 이미 커밋됐고 행만 다시 보냄으로 남는다
	@Transactional
	public void completeByResend(String messageId) {
		payoutDltUseCase.completeByResend(messageId, LocalDateTime.now(ZONE));
	}

	@Transactional(readOnly = true)
	public List<DltKafkaEventDto> findDlts(DltKafkaEventStatus status, int size) {
		return payoutDltUseCase.findByStatus(status, size).stream().map(DltKafkaEventDto::from).toList();
	}

	// 다시 보냄으로 먼저 커밋한 뒤 보낸다. 보내지 못하면 처리 실패로 되돌리고 503(DltKafkaResendException)을 던진다
	public DltKafkaEventDto resendDlt(long id) {
		PayoutDltKafkaEvent event = transactionTemplate.execute(status ->
			payoutDltUseCase.markResent(id, LocalDateTime.now(ZONE)));
		try {
			payoutDltUseCase.send(event);
		} catch (DltKafkaResendException exception) {
			transactionTemplate.executeWithoutResult(status -> payoutDltUseCase.cancelResent(id));
			throw exception;
		}
		return DltKafkaEventDto.from(event);
	}

	@Transactional
	public DltKafkaEventDto completeDlt(long id, String memo) {
		return DltKafkaEventDto.from(payoutDltUseCase.completeManually(id, memo, LocalDateTime.now(ZONE)));
	}
}
