package com.bukang.queue.in;

import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.bukang.common.global.json.JsonConverter;
import com.bukang.common.shared.kafka.KafkaMessageId;
import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.race.event.RaceChangedEvent;
import com.bukang.common.shared.race.event.RaceCourseChangedEvent;
import com.bukang.queue.app.QueueFacade;
import com.bukang.queue.app.QueueKafkaRetry;

import lombok.RequiredArgsConstructor;

/**
 * 상품 서비스가 보낸 대회·종목 변경 메시지를 받아 복제본(QueueRace, QueueRaceCourse)에 저장한다
 * 대회 메시지와 종목 메시지는 토픽이 달라 서로 순서가 보장되지 않는다. 행마다 자기 수정 일시로 옛 메시지를 가린다.
 * 메시지는 JSON 문자열로 받아 JsonConverter로 바꾼다.
 * 처리 중 예외가 나면 재시도 토픽으로 보내 간격을 늘려 가며 다시 시도하고, 그래도 실패하면 <토픽>.queue.dlt로 보낸다.
 * DLT 메시지는 QueueKafkaDltConsumer가 받는다.
 * 상품이 붙인 message-id 헤더(product-outbox-{UUID})로 인박스가 같은 메시지를 두 번 처리하지 않는다
 * (재시도·DLT 토픽으로 넘어가도 헤더는 그대로다).
 */
@Component
@RequiredArgsConstructor
public class QueueKafkaConsumer {
	// application.yaml에 group-id가 없으므로 리스너마다 적는다. 인박스에 남기는 그룹(QueueFacade.KAFKA_GROUP_ID)과 같은 값이다
	private static final String GROUP_ID = "queue-service";
	// 토픽 파티션 수. 대회·종목 토픽은 처음 보낼 때 자동으로 만들어져 브로커 기본값(1개)이므로 1로 맞춘다
	// 재시도·DLT 토픽도 같은 수로 만든다
	private static final String PARTITIONS = "1";
	// 동시에 처리할 쓰레드 수 (재시도·DLT 리스너도 같은 수). 파티션 수까지만 효과가 있다
	private static final String CONCURRENCY = "1";
	// 처음 시도를 포함한 시도 횟수. 실패하면 1초 → 2초 간격(2배수)으로 2번 다시 시도한다
	private static final String RETRY_ATTEMPTS = "3";
	// 첫 재시도 지연과 재시도·DLT 토픽 접미사는 QueueKafkaRetry에 있다. 다시 보내기(QueueDltUseCase)가 같은 값으로
	// 첫 재시도 토픽 이름(예: queuing.race.changed.queue.retry-1000)을 만든다
	private static final long RETRY_DELAY_MILLIS = QueueKafkaRetry.FIRST_RETRY_DELAY_MILLIS;
	private static final double RETRY_MULTIPLIER = 2.0;
	private static final String RETRY_TOPIC_SUFFIX = QueueKafkaRetry.RETRY_TOPIC_SUFFIX;
	// QueueKafkaDltConsumer가 DLT 토픽 이름을 만들 때 같은 값을 쓴다
	static final String DLT_TOPIC_SUFFIX = QueueKafkaRetry.DLT_TOPIC_SUFFIX;
	// DLT는 QueueKafkaDltConsumer가 받으므로, @RetryableTopic이 기본으로 띄우는 DLT 리스너는 시작하지 않는다
	// (켜 두면 같은 DLT 메시지를 두 곳에서 받는다)
	private static final String AUTO_START_DEFAULT_DLT_HANDLER = "false";

	private final QueueFacade queueFacade;
	private final JsonConverter jsonConverter;

	@KafkaListener(topics = KafkaTopics.RACE_CHANGED, groupId = GROUP_ID, concurrency = CONCURRENCY)
	@RetryableTopic(
		attempts = RETRY_ATTEMPTS,
		backOff = @BackOff(delay = RETRY_DELAY_MILLIS, multiplier = RETRY_MULTIPLIER),
		// 깨진 JSON, 모르는 Enum 값(판매 상태)은 다시 해도 실패하므로 재시도 없이 바로 DLT로 보낸다
		// (JsonConverter가 파싱 실패를 IllegalArgumentException으로 바꾼다)
		exclude = IllegalArgumentException.class,
		numPartitions = PARTITIONS,
		retryTopicSuffix = RETRY_TOPIC_SUFFIX,
		dltTopicSuffix = DLT_TOPIC_SUFFIX,
		autoStartDltHandler = AUTO_START_DEFAULT_DLT_HANDLER
	)
	public void handleRaceChanged(String payload,
		@Header(name = KafkaMessageId.HEADER, required = false) String messageId) {
		RaceChangedEvent event = jsonConverter.fromJson(payload, RaceChangedEvent.class);
		queueFacade.syncRace(messageId, KafkaTopics.RACE_CHANGED, event.raceId(), event.saleStatus(),
			event.individualOpenAt(), event.applicationCloseAt(), event.updatedAt());
	}

	@KafkaListener(topics = KafkaTopics.RACE_COURSE_CHANGED, groupId = GROUP_ID, concurrency = CONCURRENCY)
	@RetryableTopic(
		attempts = RETRY_ATTEMPTS,
		backOff = @BackOff(delay = RETRY_DELAY_MILLIS, multiplier = RETRY_MULTIPLIER),
		// 깨진 JSON은 다시 해도 실패하므로 재시도 없이 바로 DLT로 보낸다
		exclude = IllegalArgumentException.class,
		numPartitions = PARTITIONS,
		retryTopicSuffix = RETRY_TOPIC_SUFFIX,
		dltTopicSuffix = DLT_TOPIC_SUFFIX,
		autoStartDltHandler = AUTO_START_DEFAULT_DLT_HANDLER
	)
	public void handleRaceCourseChanged(String payload,
		@Header(name = KafkaMessageId.HEADER, required = false) String messageId) {
		RaceCourseChangedEvent event = jsonConverter.fromJson(payload, RaceCourseChangedEvent.class);
		queueFacade.syncRaceCourse(messageId, KafkaTopics.RACE_COURSE_CHANGED, event.raceCourseId(), event.raceId(),
			event.updatedAt(), event.deleted());
	}
}
