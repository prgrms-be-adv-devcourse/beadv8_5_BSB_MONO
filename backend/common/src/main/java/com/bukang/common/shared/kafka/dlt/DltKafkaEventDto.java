package com.bukang.common.shared.kafka.dlt;

import java.time.LocalDateTime;

/**
 * DLT 실패 행 응답 (서비스마다 있는 내부 API /internal/v1/{서비스}/kafka-failures가 쓴다)
 * 내부 API라 메시지 본문(payload)도 그대로 돌려준다. 원인을 찾고 다시 보낼지 정하는 데 필요하다
 */
public record DltKafkaEventDto(
	long id,
	DltKafkaEventStatus status,
	String originalTopic,
	String dltTopic,
	int dltPartition,
	long dltOffset,
	String messageKey,
	String messageId,
	String payload,
	String exceptionClass,
	String exceptionMessage,
	LocalDateTime createDate,
	LocalDateTime resentDate,
	LocalDateTime completedDate,
	String completeMemo
) {
	public static DltKafkaEventDto from(BaseDltKafkaEvent event) {
		return new DltKafkaEventDto(
			event.getId(),
			event.getStatus(),
			event.getOriginalTopic(),
			event.getDltTopic(),
			event.getDltPartition(),
			event.getDltOffset(),
			event.getMessageKey(),
			event.getMessageId(),
			event.getPayload(),
			event.getExceptionClass(),
			event.getExceptionMessage(),
			event.getCreateDate(),
			event.getResentDate(),
			event.getCompletedDate(),
			event.getCompleteMemo()
		);
	}
}
