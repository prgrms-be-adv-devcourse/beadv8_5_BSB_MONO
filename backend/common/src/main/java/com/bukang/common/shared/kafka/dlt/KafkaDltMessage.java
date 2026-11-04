package com.bukang.common.shared.kafka.dlt;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;

import com.bukang.common.shared.kafka.KafkaMessageId;

/**
 * DLT로 온 Kafka 메시지에서 실패 테이블(BaseDltKafkaEvent)에 남길 값을 꺼낸 것.
 * 받는 쪽: DLT 리스너가 ConsumerRecord를 받아 KafkaDltMessage.from(record, DLT 접미사)로 바꾼다.
 * 다시 보내는 쪽: KafkaDltMessage.resendRecord(firstRetryTopic(...), 키, 본문, message-id)를 kafkaTemplate.send로 보낸다.
 *
 * @param originalTopic 처음 메시지가 온 토픽 (예: queuing.member.joined)
 * @param dltTopic 메시지가 들어 있던 DLT 토픽 (예: queuing.member.joined.product.dlt). 콘솔 확인용
 * @param dltPartition DLT 토픽 안의 파티션 번호. 콘솔 확인용
 * @param dltOffset 파티션 안의 위치. 콘솔 확인용
 * @param messageKey 메시지 키. 없으면 null
 * @param payload 메시지 본문(JSON)
 * @param messageId message-id 헤더 값. 없으면 null
 * @param exceptionClass 마지막 실패의 예외 종류. 없으면 null
 * @param exceptionMessage 마지막 실패의 예외 메시지 (칸 길이에 맞게 자름). 없으면 null
 */
public record KafkaDltMessage(
	String originalTopic,
	String dltTopic,
	int dltPartition,
	long dltOffset,
	String messageKey,
	String payload,
	String messageId,
	String exceptionClass,
	String exceptionMessage
) {
	// DLT로 보낼 때 Spring Kafka(DeadLetterPublishingRecoverer)가 붙이는 예외 헤더
	// @RetryableTopic을 거친 메시지에는 kafka_exception-*(KafkaHeaders.EXCEPTION_*)가 붙는다 (로컬 Redpanda에서 확인)
	// 재시도 토픽 없이 DLT로만 보내는 설정에서 붙는 kafka_dlt-exception-*(KafkaHeaders.DLT_EXCEPTION_*)는 없을 때 대신 읽는다
	// common은 spring-kafka에 의존하지 않으므로 이름을 직접 적는다
	// 리스너에서 난 예외는 ListenerExecutionFailedException으로 감싸져 오므로, 원인 예외 종류를 먼저 본다
	static final String EXCEPTION_FQCN_HEADER = "kafka_exception-fqcn";
	static final String EXCEPTION_CAUSE_FQCN_HEADER = "kafka_exception-cause-fqcn";
	static final String EXCEPTION_MESSAGE_HEADER = "kafka_exception-message";
	static final String DLT_EXCEPTION_FQCN_HEADER = "kafka_dlt-exception-fqcn";
	static final String DLT_EXCEPTION_CAUSE_FQCN_HEADER = "kafka_dlt-exception-cause-fqcn";
	static final String DLT_EXCEPTION_MESSAGE_HEADER = "kafka_dlt-exception-message";

	// DLT 레코드를 실패 테이블에 남길 값으로 바꾼다
	// 원래 토픽은 헤더(kafka_dlt-original-topic) 대신 DLT 토픽 이름에서 접미사를 떼어 만든다
	// 헤더 값은 재시도 토픽을 거치며 바뀔 수 있지만, DLT 토픽 이름은 늘 "원래 토픽 + DLT 접미사"라 확실하다
	public static KafkaDltMessage from(ConsumerRecord<String, String> record, String dltTopicSuffix) {
		String dltTopic = record.topic();
		if (!dltTopic.endsWith(dltTopicSuffix)) {
			throw new IllegalArgumentException("DLT 토픽이 아닙니다: " + dltTopic + " (접미사 " + dltTopicSuffix + ")");
		}
		String originalTopic = dltTopic.substring(0, dltTopic.length() - dltTopicSuffix.length());

		String causeClass = headerValue(record, EXCEPTION_CAUSE_FQCN_HEADER, DLT_EXCEPTION_CAUSE_FQCN_HEADER);
		String exceptionClass = causeClass != null ? causeClass
			: headerValue(record, EXCEPTION_FQCN_HEADER, DLT_EXCEPTION_FQCN_HEADER);

		return new KafkaDltMessage(
			originalTopic,
			dltTopic,
			record.partition(),
			record.offset(),
			record.key(),
			// 본문 칸은 비울 수 없다. 본문 없는 메시지(tombstone)는 빈 문자열로 남긴다
			record.value() != null ? record.value() : "",
			headerValue(record, KafkaMessageId.HEADER),
			exceptionClass,
			BaseDltKafkaEvent.truncateExceptionMessage(
				headerValue(record, EXCEPTION_MESSAGE_HEADER, DLT_EXCEPTION_MESSAGE_HEADER))
		);
	}

	// 다시 보낼 토픽 = 그 서비스의 첫 재시도 토픽 (예: queuing.member.joined + .product.retry + -1000)
	// 원래 토픽으로 보내면 같은 토픽을 받는 다른 서비스도 다시 받으므로, 이 서비스만 받는 재시도 토픽으로 보낸다
	// 재시도 토픽은 남은 재시도(다음 재시도 토픽 → DLT)를 그대로 거친다
	// retryTopicSuffix·firstDelayMillis는 서비스 리스너의 @RetryableTopic 값과 같은 상수를 넘긴다
	public static String firstRetryTopic(String originalTopic, String retryTopicSuffix, long firstDelayMillis) {
		return originalTopic + retryTopicSuffix + "-" + firstDelayMillis;
	}

	// 다시 보낼 레코드. 새 레코드라 재시도·DLT 헤더(retry_topic-*, kafka_dlt-*)는 붙지 않는다
	// 재시도 토픽 리스너는 기다릴 시각 헤더(retry_topic-backoff-timestamp)가 없으면 바로 처리한다
	// message-id는 그대로 붙여, 이미 처리한 메시지면 인박스가 건너뛰고 또 실패하면 같은 실패 행을 찾게 한다
	public static ProducerRecord<String, String> resendRecord(String topic, String key, String payload,
		String messageId) {
		if (messageId == null) {
			return new ProducerRecord<>(topic, key, payload);
		}
		return KafkaMessageId.record(topic, key, payload, messageId);
	}

	// 먼저 찾을 헤더가 없으면 대신 찾을 헤더 값을 쓴다. 둘 다 없으면 null
	private static String headerValue(ConsumerRecord<String, String> record, String name, String fallbackName) {
		String value = headerValue(record, name);
		return value != null ? value : headerValue(record, fallbackName);
	}

	// 같은 이름의 헤더가 여러 개면(재시도를 거치며 쌓인 경우) 마지막 값을 쓴다. 없으면 null
	private static String headerValue(ConsumerRecord<String, String> record, String name) {
		Header header = record.headers().lastHeader(name);
		if (header == null || header.value() == null) {
			return null;
		}
		return new String(header.value(), StandardCharsets.UTF_8);
	}
}
