package com.bukang.order.dto;

import java.time.LocalDateTime;

import com.bukang.common.shared.kafka.outbox.OutboxStatus;
import com.bukang.order.domain.OrderOutboxKafkaEvent;

// 발행 실패 아웃박스 행 응답 (내부 API /internal/v1/order/outbox-failures, BUK-180)
// 내부 API라 본문(payload)도 그대로 돌려준다. 원인을 찾고 되돌릴지 정하는 데 필요하다
public record OutboxFailureDto(
	long id,
	OutboxStatus status,
	String messageId,
	String eventType,
	String refType,
	long refId,
	String payload,
	int retryCount,
	LocalDateTime createDate
) {
	public static OutboxFailureDto from(OrderOutboxKafkaEvent event) {
		return new OutboxFailureDto(
			event.getId(),
			event.getStatus(),
			event.getMessageId(),
			event.getTopic(),
			event.getRefType(),
			event.getRefId(),
			event.getPayload(),
			event.getRetryCount(),
			event.getCreateDate()
		);
	}
}
