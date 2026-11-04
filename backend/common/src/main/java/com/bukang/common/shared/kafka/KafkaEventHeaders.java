package com.bukang.common.shared.kafka;

/**
 * 아웃박스로 보내는 Kafka 메시지에 붙이는 헤더 이름 (받는 쪽이 같은 상수로 읽는다)
 * 값은 모두 UTF-8 문자열이다. common에는 Kafka 라이브러리가 없어 이름만 둔다
 */
public final class KafkaEventHeaders {
	// 이벤트 ID (UUID). 본문의 eventId와 같은 값이라 받는 쪽이 중복을 거를 때 쓴다
	public static final String EVENT_ID = "eventId";
	// 이벤트 종류. 보낸 토픽 이름과 같다
	public static final String EVENT_TYPE = "eventType";
	// 이벤트가 생긴 시각. 본문 시각과 같은 형식(한국 시간, 시간대 표기 없음)이라 LocalDateTime.parse로 읽는다
	public static final String OCCURRED_AT = "occurredAt";
	// 이벤트 대상 종류 (예: Member, Seller)
	public static final String AGGREGATE_TYPE = "aggregateType";
	// 이벤트 대상 ID (숫자 문자열). 메시지 key와 같다
	public static final String AGGREGATE_ID = "aggregateId";

	private KafkaEventHeaders() {
	}
}
