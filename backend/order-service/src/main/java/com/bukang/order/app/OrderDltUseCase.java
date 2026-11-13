package com.bukang.order.app;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.data.domain.Limit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.common.global.notify.SlackNotifier;
import com.bukang.common.shared.kafka.dlt.DltKafkaEventNotFoundException;
import com.bukang.common.shared.kafka.dlt.DltKafkaEventStatus;
import com.bukang.common.shared.kafka.dlt.DltKafkaEventStatusException;
import com.bukang.common.shared.kafka.dlt.DltKafkaResendException;
import com.bukang.common.shared.kafka.dlt.KafkaDltAlert;
import com.bukang.common.shared.kafka.dlt.KafkaDltMessage;
import com.bukang.order.domain.OrderDltKafkaEvent;
import com.bukang.order.out.OrderDltKafkaEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 재시도를 모두 실패해 DLT로 간 메시지를 실패 표(ORDER_DLT_KAFKA_EVENT)에 남기고, 다시 보내거나 처리 완료로 닫는다 (BUK-180).
 * 상품 ProductDltUseCase와 같은 방식이다. 메시지 하나는 행 하나다(message_id로 찾는다). DLT로 올 때마다 처리 실패로 두고 커밋 뒤 Slack으로 알린다.
 * 자동으로 다시 보내지 않는다. 재시도는 @RetryableTopic이 이미 했으므로, 사람이 원인을 고친 뒤 다시 보내거나 닫는다.
 * 주문은 UseCase가 트랜잭션을 연다. send만 트랜잭션 밖이고, 다시 보내기 순서는 OrderKafkaFailureFacade가 묶는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderDltUseCase {
	// 브로커 응답을 기다리는 최대 시간
	private static final long SEND_TIMEOUT_SECONDS = 5;

	private final OrderDltKafkaEventRepository orderDltKafkaEventRepository;
	private final KafkaTemplate<String, String> kafkaTemplate;
	private final SlackNotifier slackNotifier;

	// DLT 리스너가 받은 메시지를 남긴다
	// 행이 없으면 처리 실패로 새로 넣고 알린다. 다시 보낸 메시지가 또 실패했으면 같은 행을 처리 실패로 되돌리고 알린다
	// 그 밖(같은 DLT 메시지를 두 번 받음 등)은 아무것도 하지 않는다. message-id가 없는 메시지는 찾을 수 없어 늘 새 행이다
	@Transactional
	public void record(KafkaDltMessage message) {
		OrderDltKafkaEvent event = message.messageId() == null ? null
			: orderDltKafkaEventRepository.findByMessageId(message.messageId()).orElse(null);

		if (event == null) {
			OrderDltKafkaEvent saved = orderDltKafkaEventRepository.save(new OrderDltKafkaEvent(message));
			slackNotifier.sendAfterCommit(KafkaDltAlert.text(saved));
			return;
		}
		boolean failedAgain = event.markFailedAgain(message.dltTopic(), message.dltPartition(), message.dltOffset(),
			message.exceptionClass(), message.exceptionMessage());
		if (failedAgain) {
			slackNotifier.sendAfterCommit(KafkaDltAlert.text(event));
			return;
		}
		log.info("이미 남긴 DLT 메시지라 건너뜁니다. id={}, messageId={}, status={}",
			event.getId(), event.getMessageId(), event.getStatus());
	}

	// 다시 보내기 1단계: 처리 실패 → 다시 보냄. 이 트랜잭션을 커밋한 뒤 send를 부른다
	// 먼저 커밋해야, 보낸 메시지를 본 리스너가 곧바로 처리해도 이 행을 다시 보냄으로 보고 처리 완료로 바꾼다
	// 엔티티의 markResent 대신 조건부 UPDATE를 쓴다. 동시에 두 번 눌러도 한 번만 통과하고 나머지는 409다 (PR #436 리뷰)
	@Transactional
	public OrderDltKafkaEvent markResent(long id, LocalDateTime now) {
		if (orderDltKafkaEventRepository.markResent(id, now) == 0) {
			throw new DltKafkaEventStatusException(OrderDltKafkaEvent.SERVICE, id, findById(id).getStatus(), "다시 보낼");
		}
		return findById(id);
	}

	// 다시 보내기 2단계: 주문의 첫 재시도 토픽으로 보낸다 (트랜잭션 밖). 같은 토픽을 받는 다른 서비스는 받지 않는다
	// 보내지 못하면 예외를 던진다. 부른 쪽이 cancelResent로 처리 실패로 되돌린다
	public void send(OrderDltKafkaEvent event) {
		String topic = KafkaDltMessage.firstRetryTopic(event.getOriginalTopic(), OrderKafkaRetry.RETRY_TOPIC_SUFFIX,
			OrderKafkaRetry.FIRST_RETRY_DELAY_MILLIS);
		try {
			kafkaTemplate.send(KafkaDltMessage.resendRecord(topic, event.getMessageKey(), event.getPayload(),
				event.getMessageId())).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new DltKafkaResendException(event.serviceName(), event.getId(), exception);
		} catch (ExecutionException | TimeoutException | RuntimeException exception) {
			throw new DltKafkaResendException(event.serviceName(), event.getId(), exception);
		}
		log.info("DLT 메시지를 다시 보냈습니다. id={}, topic={}, messageId={}", event.getId(), topic, event.getMessageId());
	}

	// 다시 보내기 3단계(보내지 못했을 때): 다시 보냄 → 처리 실패. 그사이 처리 완료가 됐으면 그대로 둔다
	@Transactional
	public void cancelResent(long id) {
		orderDltKafkaEventRepository.cancelResent(id, LocalDateTime.now());
	}

	// 본 리스너가 메시지를 처리했거나 이미 처리한 메시지라 건너뛸 때 부른다. 다시 보낸 메시지였으면 그 행을 처리 완료로 바꾼다
	// 다시 보낸 메시지가 아니면(행이 없거나 다시 보냄이 아님) 아무것도 하지 않는다
	@Transactional
	public void completeByResend(String messageId, LocalDateTime now) {
		if (messageId == null) {
			return;
		}
		orderDltKafkaEventRepository.findByMessageId(messageId)
			.filter(event -> event.completeByResend(now))
			.ifPresent(event -> log.info("다시 보낸 메시지를 처리해 처리 완료로 바꿨습니다. id={}, messageId={}",
				event.getId(), messageId));
	}

	// 사람이 원인을 고친 뒤 메모와 함께 닫는다
	@Transactional
	public OrderDltKafkaEvent completeManually(long id, String memo, LocalDateTime now) {
		OrderDltKafkaEvent event = findById(id);
		event.completeManually(now, memo);
		return event;
	}

	// 상태별 행을 최근 것부터 limit건 (내부 API 목록)
	@Transactional(readOnly = true)
	public List<OrderDltKafkaEvent> findByStatus(DltKafkaEventStatus status, int limit) {
		return orderDltKafkaEventRepository.findByStatusOrderByIdDesc(status, Limit.of(limit));
	}

	private OrderDltKafkaEvent findById(long id) {
		return orderDltKafkaEventRepository.findById(id)
			.orElseThrow(() -> new DltKafkaEventNotFoundException(OrderDltKafkaEvent.SERVICE, id));
	}
}
