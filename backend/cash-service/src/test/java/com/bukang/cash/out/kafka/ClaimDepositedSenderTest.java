package com.bukang.cash.out.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import com.bukang.common.global.json.JsonConverter;
import com.bukang.common.shared.cash.event.ClaimDepositedEvent;
import com.bukang.common.shared.kafka.KafkaMessageId;
import com.bukang.common.shared.kafka.KafkaTopics;

import tools.jackson.databind.json.JsonMapper;

// 정산에 "청구 입금됨"을 보내는 Kafka 어댑터 테스트. 브로커 없이 KafkaTemplate만 목으로 바꾼다
class ClaimDepositedSenderTest {
	private static final ClaimDepositedEvent EVENT =
		new ClaimDepositedEvent("RC-20261214-0A3F", 400_000, LocalDateTime.of(2026, 12, 20, 14, 2));

	@SuppressWarnings("unchecked")
	private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
	private final JsonConverter jsonConverter = new JsonConverter(JsonMapper.builder().build());
	private final ClaimDepositedSender sender = new ClaimDepositedSender(kafkaTemplate, jsonConverter);

	@Test
	@DisplayName("청구 입금 토픽에 청구 ID를 key로, 청구 ID로 만든 메시지 ID 헤더를 붙여 보낸다")
	void sendsToClaimDepositedTopic() {
		given(kafkaTemplate.send(any(ProducerRecord.class))).willReturn(CompletableFuture.completedFuture(null));

		sender.send(EVENT);

		ProducerRecord<String, String> record = sentRecord();
		assertThat(record.topic()).isEqualTo(KafkaTopics.CLAIM_DEPOSITED);
		assertThat(record.key()).isEqualTo("RC-20261214-0A3F");
		assertThat(jsonConverter.fromJson(record.value(), ClaimDepositedEvent.class)).isEqualTo(EVENT);
		assertThat(new String(record.headers().lastHeader(KafkaMessageId.HEADER).value(), StandardCharsets.UTF_8))
			.isEqualTo("cash-claimdeposited-RC-20261214-0A3F");
	}

	@SuppressWarnings("unchecked")
	private ProducerRecord<String, String> sentRecord() {
		ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
		verify(kafkaTemplate).send(captor.capture());
		return captor.getValue();
	}
}
