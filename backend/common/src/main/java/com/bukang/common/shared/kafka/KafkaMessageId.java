package com.bukang.common.shared.kafka;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.apache.kafka.clients.producer.ProducerRecord;

/**
 * Kafka 메시지 ID 헤더. 받는 서비스가 인박스로 같은 메시지를 두 번 처리하지 않게 한다
 * 아웃박스 행을 만들 때: this.messageId = KafkaMessageId.ofOutbox("product") (행에 저장)
 * 보내는 쪽: send(KafkaMessageId.record(topic, key, payload, event.getMessageId())) (저장한 값을 그대로 보낸다)
 * 받는 쪽: @Header(name = KafkaMessageId.HEADER, required = false) String messageId (헤더가 없으면 null → 인박스 없이 처리)
 */
public final class KafkaMessageId {
	public static final String HEADER = "message-id";

	private KafkaMessageId() {
	}

	// 아웃박스 메시지 ID를 새로 만든다 (예: product-outbox-3f2a9c1e-…)
	// 아웃박스 행을 만들 때 한 번만 불러 행에 저장하고, 릴레이는 저장한 값을 보낸다.
	// 보낼 때마다 부르면 다시 보낼 때 값이 달라져 받는 쪽 인박스가 같은 메시지를 거르지 못한다.
	// 행 번호 대신 UUID라 아웃박스 표를 새로 만들어도(번호가 1부터 다시 시작) 받는 쪽에 남은 옛 ID와 겹치지 않는다
	public static String ofOutbox(String service) {
		return service + "-outbox-" + UUID.randomUUID();
	}

	// 메시지 ID 헤더를 붙인 레코드. kafkaTemplate.send(ProducerRecord)로 보낸다
	public static ProducerRecord<String, String> record(String topic, String key, String payload, String messageId) {
		ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, payload);
		record.headers().add(HEADER, messageId.getBytes(StandardCharsets.UTF_8));
		return record;
	}
}
