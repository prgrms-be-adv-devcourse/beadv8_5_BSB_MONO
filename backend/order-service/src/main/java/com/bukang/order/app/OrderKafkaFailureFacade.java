package com.bukang.order.app;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.bukang.common.shared.kafka.dlt.DltKafkaEventDto;
import com.bukang.common.shared.kafka.dlt.DltKafkaEventStatus;
import com.bukang.common.shared.kafka.dlt.DltKafkaResendException;
import com.bukang.common.shared.kafka.dlt.KafkaDltMessage;
import com.bukang.order.domain.OrderDltKafkaEvent;
import com.bukang.order.dto.OutboxFailureDto;

import lombok.RequiredArgsConstructor;

/**
 * Kafka로 주고받다 끝내 실패한 메시지를 사람이 다루는 길 (BUK-180).
 * - 받는 쪽: DLT로 온 메시지를 실패 표에 남기고, 다시 보내거나 처리 완료로 닫는다 (OrderDltUseCase)
 * - 보내는 쪽: 발행 실패(SEND_FAILED)가 된 아웃박스 행을 PENDING으로 되돌린다 (OrderOutboxKafkaRelayUseCase)
 * 트랜잭션은 UseCase가 단계마다 연다. 다시 보내기는 표시를 커밋한 뒤 트랜잭션 밖에서 보내야 하기 때문이다.
 */
@Service
@RequiredArgsConstructor
public class OrderKafkaFailureFacade {
	private final OrderDltUseCase orderDltUseCase;
	private final OrderOutboxKafkaRelayUseCase orderOutboxKafkaRelayUseCase;

	// 재시도를 모두 실패해 DLT로 온 메시지를 실패 표에 남기고, 커밋 뒤 Slack으로 알린다 (OrderKafkaDltConsumer가 부른다)
	public void recordDlt(KafkaDltMessage message) {
		orderDltUseCase.record(message);
	}

	public List<DltKafkaEventDto> findDlts(DltKafkaEventStatus status, int size) {
		return orderDltUseCase.findByStatus(status, size).stream().map(DltKafkaEventDto::from).toList();
	}

	// 다시 보냄으로 커밋 → 첫 재시도 토픽으로 보내기 → 보내지 못하면 처리 실패로 되돌리고 503
	public DltKafkaEventDto resendDlt(long id) {
		OrderDltKafkaEvent event = orderDltUseCase.markResent(id, LocalDateTime.now());
		try {
			orderDltUseCase.send(event);
		} catch (DltKafkaResendException exception) {
			orderDltUseCase.cancelResent(id);
			throw exception;
		}
		return DltKafkaEventDto.from(event);
	}

	public DltKafkaEventDto completeDlt(long id, String memo) {
		return DltKafkaEventDto.from(orderDltUseCase.completeManually(id, memo, LocalDateTime.now()));
	}

	public List<OutboxFailureDto> findOutboxFailures(int size) {
		return orderOutboxKafkaRelayUseCase.findSendFailed(size).stream().map(OutboxFailureDto::from).toList();
	}

	public OutboxFailureDto retryOutbox(long id) {
		return OutboxFailureDto.from(orderOutboxKafkaRelayUseCase.retry(id, LocalDateTime.now()));
	}
}
