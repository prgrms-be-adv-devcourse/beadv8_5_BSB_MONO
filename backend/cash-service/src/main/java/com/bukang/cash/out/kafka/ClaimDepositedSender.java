package com.bukang.cash.out.kafka;

import java.util.concurrent.TimeUnit;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.bukang.cash.domain.exception.recovery.ClaimDepositedSendFailedException;
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

	// 브로커가 받았다고 답한 뒤에 돌아온다. 받지 못하면 ClaimDepositedSendFailedException을 던진다
	// 청구 하나에 메시지가 하나라, 메시지 ID를 청구 ID로 만들면 다시 보내도 같은 값이 나간다 (KafkaMessageId 인박스 규칙)
	// 브로커에 연결하지 못하면 send()가 응답 대기 전에 멈추는데, 그 한도는 application.yaml의 max.block.ms(3초)다
	public void send(ClaimDepositedEvent event) {
		try {
			kafkaTemplate.send(KafkaMessageId.record(KafkaTopics.CLAIM_DEPOSITED, event.claimCode(),
					jsonConverter.toJson(event), MESSAGE_ID_PREFIX + event.claimCode()))
				.orTimeout(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
				.join();
		} catch (RuntimeException exception) {
			throw new ClaimDepositedSendFailedException(
				"청구 입금 메시지를 보내지 못했습니다. claimCode=" + event.claimCode(), exception);
		}
	}
}
