package com.bukang.common.shared.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import com.bukang.common.global.json.JsonConverter;
import com.bukang.common.shared.kafka.KafkaMessageId;
import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.notification.event.NotificationRequestedEvent;

import tools.jackson.databind.json.JsonMapper;

class NotificationKafkaSenderTest {
	@SuppressWarnings("unchecked")
	private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
	private final JsonConverter jsonConverter = new JsonConverter(JsonMapper.builder().build());
	private final NotificationKafkaSender sender = new NotificationKafkaSender(kafkaTemplate, jsonConverter);

	private final NotificationRequestedEvent event = new NotificationRequestedEvent("event-1", 7L,
		NotificationKind.REFUND_COMPLETED, "환불이 끝났어요", "본문", "/me/deposit", LocalDateTime.of(2026, 11, 4, 9, 30));

	@Test
	@DisplayName("알림 토픽에 회원 ID를 key로, 메시지 ID를 헤더로 붙여 보낸다")
	@SuppressWarnings("unchecked")
	void sendsRecord() {
		when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(new CompletableFuture<>());

		sender.send(event);

		ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
		verify(kafkaTemplate, timeout(2000)).send(captor.capture());
		ProducerRecord<String, String> record = captor.getValue();
		assertThat(record.topic()).isEqualTo(KafkaTopics.NOTIFICATION_REQUESTED);
		assertThat(record.key()).isEqualTo("7");
		assertThat(new String(record.headers().lastHeader(KafkaMessageId.HEADER).value(),
			StandardCharsets.UTF_8)).isEqualTo("event-1");
		assertThat(jsonConverter.fromJson(record.value(), NotificationRequestedEvent.class)).isEqualTo(event);
	}

	@Test
	@DisplayName("Kafka가 예외를 던져도 부른 쪽에는 예외가 나지 않는다 (알림은 버린다)")
	@SuppressWarnings("unchecked")
	void swallowsFailure() {
		when(kafkaTemplate.send(any(ProducerRecord.class))).thenThrow(new IllegalStateException("broker down"));

		assertThatCode(() -> sender.send(event)).doesNotThrowAnyException();
		verify(kafkaTemplate, timeout(2000)).send(any(ProducerRecord.class));
	}
}
