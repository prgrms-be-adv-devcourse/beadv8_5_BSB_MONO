package com.bukang.common.shared.kafka;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.clients.producer.ProducerRecord;

/**
 * Kafka 메시지 ID 헤더. 받는 서비스가 인박스로 같은 메시지를 두 번 처리하지 않게 한다
 * 보내는 쪽: send(KafkaMessageId.record(topic, key, payload, KafkaMessageId.ofOutbox("product", event.getId())))
 * 받는 쪽: @Header(name = KafkaMessageId.HEADER, required = false) String messageId (헤더가 없으면 null → 인박스 없이 처리)
 */
public final class KafkaMessageId {
	public static final String HEADER = "message-id";

	private KafkaMessageId() {
	}

	// 아웃박스 행으로 메시지 ID를 만든다 (예: product-outbox-42)
	// 릴레이가 같은 행을 다시 보내도 같은 값이고, 서비스 이름이 붙어 다른 서비스의 같은 행 번호와 겹치지 않는다
	public static String ofOutbox(String service, long outboxId) {
		return service + "-outbox-" + outboxId;
	}

	// 메시지 ID 헤더를 붙인 레코드. kafkaTemplate.send(ProducerRecord)로 보낸다
	public static ProducerRecord<String, String> record(String topic, String key, String payload, String messageId) {
		ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, payload);
		record.headers().add(HEADER, messageId.getBytes(StandardCharsets.UTF_8));
		return record;
	}
}
