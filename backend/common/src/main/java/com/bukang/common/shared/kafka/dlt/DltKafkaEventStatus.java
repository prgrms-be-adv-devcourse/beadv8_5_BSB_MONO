package com.bukang.common.shared.kafka.dlt;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * DLT로 간 Kafka 메시지 행(BaseDltKafkaEvent)의 상태.
 * 처리 실패(DLT로 오면 Slack으로 알림) → 사람이 원인을 고친 뒤
 *   ├ 다시 보냄 → 처리되면 처리 완료(자동) / 또 실패하면 처리 실패로 돌아가 다시 알림
 *   └ 처리 완료(사람이 메모와 함께 닫음)
 */
@Getter
@RequiredArgsConstructor
public enum DltKafkaEventStatus {
	// 재시도를 모두 실패해 DLT로 왔고 Slack으로 알렸다. 사람이 원인을 고친 뒤 다시 보내거나 처리 완료로 닫는다
	FAILED("처리 실패"),
	// 사람이 내부 API로 메시지를 다시 보냈다. 처리되면 처리 완료, 또 실패하면 처리 실패로 돌아간다
	RESENT("다시 보냄"),
	// 처리를 끝냈다. 더는 바꾸지 않는다
	COMPLETED("처리 완료");

	// 화면과 메시지에 쓰는 이름
	private final String label;
}
