package com.bukang.ai.in;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.bukang.ai.app.RecordCertCheckUseCase;
import com.bukang.ai.config.AiKafkaConfig;
import com.bukang.common.global.json.JsonConverter;
import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.recordcert.event.RecordCertCheckRequestedEvent;

import lombok.RequiredArgsConstructor;

/**
 * 기록증 서버 확인 요청을 받는다 (BUK-275). 메모리(512Mi) 때문에 쓰레드 하나로 한 번에 한 장만 읽는다
 * 처리가 예외로 끝나면(결과 보내기 실패) AiKafkaConfig의 오류 처리가 offset을 넘기지 않고 같은 요청을 다시 처리한다
 */
@Component
@RequiredArgsConstructor
public class RecordCertCheckListener {
	// 바꾸면 새 그룹으로 보고 토픽을 처음부터 다시 읽는다 (auto-offset-reset: earliest)
	private static final String GROUP_ID = "ai";
	private static final String CONCURRENCY = "1";

	private final RecordCertCheckUseCase recordCertCheckUseCase;
	private final JsonConverter jsonConverter;

	@KafkaListener(topics = KafkaTopics.RECORD_CERT_CHECK_REQUESTED, groupId = GROUP_ID, concurrency = CONCURRENCY,
		containerFactory = AiKafkaConfig.LISTENER_FACTORY)
	public void handleCheckRequested(String payload) {
		recordCertCheckUseCase.check(jsonConverter.fromJson(payload, RecordCertCheckRequestedEvent.class));
	}
}
