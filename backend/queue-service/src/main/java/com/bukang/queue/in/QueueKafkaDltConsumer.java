package com.bukang.queue.in;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.kafka.dlt.KafkaDltMessage;
import com.bukang.queue.app.QueueFacade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 재시도를 모두 실패했거나 재시도할 필요가 없는 실패로 DLT(<토픽>.queue.dlt)에 간 메시지를 받아
 * 실패 테이블(QUEUE_DLT_KAFKA_EVENT)에 남기고 Slack으로 알린다.
 * 자동으로 다시 처리하지 않는다. 원인을 고친 뒤 내부 API(/internal/v1/queue/kafka-failures/{id}/resend)로 다시 보낸다.
 * 헤더(message-id, 예외 정보)를 읽어야 하므로 본문만이 아니라 ConsumerRecord로 받는다.
 * 테이블에 남기다 예외가 나면 기본 오류 처리기가 같은 메시지를 다시 시도하고, 끝내 실패하면 에러 로그를 남기고 건너뛴다
 * (이때도 메시지는 DLT 토픽과 아래 에러 로그에 남는다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueueKafkaDltConsumer {
	// 본 리스너(queue-service)와 오프셋을 따로 관리한다
	private static final String GROUP_ID = "queue-service-dlt";
	private static final String RACE_CHANGED_DLT = KafkaTopics.RACE_CHANGED
		+ QueueKafkaConsumer.DLT_TOPIC_SUFFIX;
	private static final String RACE_COURSE_CHANGED_DLT = KafkaTopics.RACE_COURSE_CHANGED
		+ QueueKafkaConsumer.DLT_TOPIC_SUFFIX;

	private final QueueFacade queueFacade;

	// 반영하지 못한 대회는 복제본이 옛 판매 상태·일정으로 남는다. 원인을 고친 뒤 메시지를 다시 보내야 한다
	@KafkaListener(topics = RACE_CHANGED_DLT, groupId = GROUP_ID)
	public void handleRaceChangedDlt(ConsumerRecord<String, String> record) {
		log.error("처리하지 못한 대회 변경 메시지: {}", record.value());
		record(record);
	}

	// 반영하지 못한 종목은 복제본에 없거나 뺀 종목이 남는다. 원인을 고친 뒤 메시지를 다시 보내야 한다
	@KafkaListener(topics = RACE_COURSE_CHANGED_DLT, groupId = GROUP_ID)
	public void handleRaceCourseChangedDlt(ConsumerRecord<String, String> record) {
		log.error("처리하지 못한 종목 변경 메시지: {}", record.value());
		record(record);
	}

	// DLT 레코드에서 원래 토픽·DLT 위치·message-id·예외를 꺼내 실패 테이블에 남긴다
	private void record(ConsumerRecord<String, String> record) {
		queueFacade.recordDlt(KafkaDltMessage.from(record, QueueKafkaConsumer.DLT_TOPIC_SUFFIX));
	}
}
