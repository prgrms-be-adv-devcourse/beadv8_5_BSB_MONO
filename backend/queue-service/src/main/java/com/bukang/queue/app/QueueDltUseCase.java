package com.bukang.queue.app;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.data.domain.Limit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.bukang.common.global.notify.SlackNotifier;
import com.bukang.common.shared.kafka.dlt.DltKafkaEventNotFoundException;
import com.bukang.common.shared.kafka.dlt.DltKafkaEventStatus;
import com.bukang.common.shared.kafka.dlt.DltKafkaResendException;
import com.bukang.common.shared.kafka.dlt.KafkaDltAlert;
import com.bukang.common.shared.kafka.dlt.KafkaDltMessage;
import com.bukang.queue.domain.QueueDltKafkaEvent;
import com.bukang.queue.out.QueueDltKafkaEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 재시도를 모두 실패해 DLT로 간 메시지를 실패 테이블(QUEUE_DLT_KAFKA_EVENT)에 남기고, 다시 보내거나 처리 완료로 닫는다.
 * 메시지 하나는 행 하나다(message_id로 찾는다). DLT로 올 때마다 처리 실패로 두고 커밋 뒤 Slack으로 알린다.
 * 자동으로 다시 보내지 않는다. 재시도는 @RetryableTopic이 이미 했으므로, 사람이 원인을 고친 뒤 다시 보내거나 닫는다.
 * 트랜잭션은 QueueFacade가 연다 (send만 트랜잭션 밖에서 부른다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueueDltUseCase {
	// 브로커 응답을 기다리는 최대 시간
	private static final long SEND_TIMEOUT_SECONDS = 5;

	private final QueueDltKafkaEventRepository queueDltKafkaEventRepository;
	private final KafkaTemplate<String, String> kafkaTemplate;
	private final SlackNotifier slackNotifier;

	// DLT 리스너가 받은 메시지를 남긴다
	// 행이 없으면 처리 실패로 새로 넣고 알린다. 다시 보낸 메시지가 또 실패했으면 같은 행을 처리 실패로 되돌리고 알린다
	// 그 밖(같은 DLT 메시지를 두 번 받음 등)은 아무것도 하지 않는다. message-id가 없는 메시지는 찾을 수 없어 늘 새 행이다
	public void record(KafkaDltMessage message) {
		QueueDltKafkaEvent event = message.messageId() == null ? null
			: queueDltKafkaEventRepository.findByMessageId(message.messageId()).orElse(null);

		if (event == null) {
			QueueDltKafkaEvent saved = queueDltKafkaEventRepository.save(new QueueDltKafkaEvent(message));
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
	public QueueDltKafkaEvent markResent(long id, LocalDateTime now) {
		QueueDltKafkaEvent event = findById(id);
		event.markResent(now);
		return event;
	}

	// 다시 보내기 2단계: 첫 재시도 토픽으로 보낸다 (트랜잭션 밖). 이 서비스의 재시도 토픽이라 같은 토픽을 받는 다른 서비스는 받지 않는다
	// 보내지 못하면 예외를 던진다. 부른 쪽이 cancelResent로 처리 실패로 되돌린다
	public void send(QueueDltKafkaEvent event) {
		String topic = KafkaDltMessage.firstRetryTopic(event.getOriginalTopic(), QueueKafkaRetry.RETRY_TOPIC_SUFFIX,
			QueueKafkaRetry.FIRST_RETRY_DELAY_MILLIS);
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

	// 다시 보내기 3단계(보내지 못했을 때): 다시 보냄 → 처리 실패
	public void cancelResent(long id) {
		findById(id).cancelResent();
	}

	// 본 리스너가 메시지를 처리한 트랜잭션에서 부른다. 다시 보낸 메시지였으면 그 행을 처리 완료로 바꾼다
	// 다시 보낸 메시지가 아니면(행이 없거나 다시 보냄이 아님) 아무것도 하지 않는다. 본 처리를 롤백시키지 않도록 예외를 내지 않는다
	// 처리 완료로 바꿨으면 본 처리가 커밋된 뒤 Slack으로 알린다 (롤백되면 보내지 않는다)
	public void completeByResend(String messageId, LocalDateTime now) {
		if (messageId == null) {
			return;
		}
		queueDltKafkaEventRepository.findByMessageId(messageId)
			.filter(event -> event.completeByResend(now))
			.ifPresent(event -> {
				log.info("다시 보낸 메시지를 처리해 처리 완료로 바꿨습니다. id={}, messageId={}", event.getId(), messageId);
				slackNotifier.sendAfterCommit(KafkaDltAlert.completedText(event));
			});
	}

	// 사람이 원인을 고친 뒤 메모와 함께 닫고, 커밋 뒤 Slack으로 알린다
	public QueueDltKafkaEvent completeManually(long id, String memo, LocalDateTime now) {
		QueueDltKafkaEvent event = findById(id);
		event.completeManually(now, memo);
		slackNotifier.sendAfterCommit(KafkaDltAlert.completedText(event));
		return event;
	}

	// 상태별 행을 최근 것부터 limit건 (내부 API 목록)
	public List<QueueDltKafkaEvent> findByStatus(DltKafkaEventStatus status, int limit) {
		return queueDltKafkaEventRepository.findByStatusOrderByIdDesc(status, Limit.of(limit));
	}

	private QueueDltKafkaEvent findById(long id) {
		return queueDltKafkaEventRepository.findById(id)
			.orElseThrow(() -> new DltKafkaEventNotFoundException(QueueDltKafkaEvent.SERVICE, id));
	}
}
