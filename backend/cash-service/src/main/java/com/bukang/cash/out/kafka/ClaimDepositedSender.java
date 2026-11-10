package com.bukang.cash.out.kafka;

import java.util.concurrent.TimeUnit;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.bukang.common.global.json.JsonConverter;
import com.bukang.common.shared.cash.event.ClaimDepositedEvent;
import com.bukang.common.shared.kafka.KafkaMessageId;
import com.bukang.common.shared.kafka.KafkaTopics;

import lombok.RequiredArgsConstructor;

/**
 * "청구 입금됨"을 Kafka로 보낸다 (queuing.claim.deposited, key 청구 ID)
 * 무엇을 언제 보낼지는 CashRecoveryDepositRelayUseCase가 정하고, 이 클래스는 메시지 모양과 브로커 응답 대기만 맡는다
 */
@Component
@RequiredArgsConstructor
public class ClaimDepositedSender {
	// 브로커 응답을 기다리는 최대 시간
	private static final long SEND_TIMEOUT_SECONDS = 5;
	private static final String MESSAGE_ID_PREFIX = "cash-claimdeposited-";

	private final KafkaTemplate<String, String> kafkaTemplate;
	private final JsonConverter jsonConverter;

	// 브로커가 받았다고 답한 뒤에 돌아온다
	// 청구 하나에 메시지가 하나라, 메시지 ID를 청구 ID로 만들면 다시 보내도 같은 값이 나간다 (KafkaMessageId 인박스 규칙)
	public void send(ClaimDepositedEvent event) {
		kafkaTemplate.send(KafkaMessageId.record(KafkaTopics.CLAIM_DEPOSITED, event.claimCode(),
				jsonConverter.toJson(event), MESSAGE_ID_PREFIX + event.claimCode()))
			.orTimeout(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
			.join();
	}
}
