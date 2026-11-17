package com.bukang.common.shared.notification;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.bukang.common.global.json.JsonConverter;
import com.bukang.common.shared.kafka.KafkaEventHeaders;
import com.bukang.common.shared.kafka.KafkaMessageId;
import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.notification.event.NotificationRequestedEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * 알림 요청(NotificationRequestedEvent)을 커밋된 뒤에 Kafka로 보낸다. 트랜잭션 밖에서 발행하면 바로 보낸다
 * 보내지 못하면 로그만 남긴다 (알림은 잃어도 되는 메시지, 11/04 결정). 예외를 던지지 않아 부른 쪽 응답에 영향이 없다
 * 주의: 다른 AFTER_COMMIT 리스너 안에서 발행하면 이미 커밋 단계라 보내지지 않는다. 트랜잭션 안(@EventListener 포함)에서 발행한다
 */
@Slf4j
@Component
public class NotificationKafkaSender {
	private final KafkaTemplate<String, String> kafkaTemplate;
	private final JsonConverter jsonConverter;
	private final TaskExecutor taskExecutor;

	// Spring Boot가 만드는 작업 실행기. 쓰레드가 앱 클래스 로더를 이어받아, jar로 띄워도 Kafka 클래스를 찾는다
	public NotificationKafkaSender(
		KafkaTemplate<String, String> kafkaTemplate,
		JsonConverter jsonConverter,
		@Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor
	) {
		this.kafkaTemplate = kafkaTemplate;
		this.jsonConverter = jsonConverter;
		this.taskExecutor = taskExecutor;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void send(NotificationRequestedEvent event) {
		// 커밋 뒤 콜백에서 예외가 나면 부른 쪽이 커밋을 실패로 보므로, 메시지를 만드는 일까지 모두 잡는다
		// 브로커가 없으면 send()가 max.block.ms(기본 60초)까지 멈추므로, 커밋을 마친 요청 쓰레드가 기다리지 않게 따로 보낸다
		// 공용 ForkJoin 풀은 쓰지 않는다. 그 쓰레드는 시스템 클래스 로더라 jar 안의 Kafka 클래스를 찾지 못하고,
		// 처음 보낼 때 Kafka 설정 클래스 초기화가 실패하면 앱을 다시 띄울 때까지 그 앱의 Kafka 전송이 모두 막힌다
		CompletableFuture.supplyAsync(() -> kafkaTemplate.send(record(event)), taskExecutor)
			.thenCompose(sent -> sent)
			.exceptionally(failure -> {
				log.warn("알림을 보내지 못했습니다 (버림). eventId={}, memberId={}, kind={}, cause={}",
					event.eventId(), event.memberId(), event.kind(), failure.toString());
				return null;
			});
	}

	// message-id 헤더(인박스 규칙, KafkaMessageId)와 본문 eventId에 같은 값을 넣는다
	private ProducerRecord<String, String> record(NotificationRequestedEvent event) {
		ProducerRecord<String, String> record = KafkaMessageId.record(KafkaTopics.NOTIFICATION_REQUESTED,
			String.valueOf(event.memberId()), jsonConverter.toJson(event), event.eventId());
		Headers headers = record.headers();
		addHeader(headers, KafkaEventHeaders.EVENT_TYPE, KafkaTopics.NOTIFICATION_REQUESTED);
		addHeader(headers, KafkaEventHeaders.OCCURRED_AT,
			event.occurredAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
		return record;
	}

	private static void addHeader(Headers headers, String name, String value) {
		headers.add(name, value.getBytes(StandardCharsets.UTF_8));
	}
}
