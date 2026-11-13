package com.bukang.common.shared.crew.event;

import java.time.LocalDateTime;

/**
 * 크루장이 바뀌었다 (BUK-99, 크루장 넘기기). 회원 서비스가 두 크루원의 역할을 바꾼 트랜잭션에서 아웃박스로 남기고, 커밋 뒤 보낸다.
 * 이전 크루장은 크루원이 된다. 파티장 넘기기(주문 서비스)와는 다른 이벤트다.
 * 크루 ID를 key로 보내 같은 크루의 이벤트는 순서대로 간다. 개인정보는 담지 않는다
 *
 * @param eventId 받는 쪽이 같은 이벤트를 두 번 처리하지 않게 거르는 값 (UUID, Kafka 헤더 eventId와 같다)
 * @param changedAt 역할을 바꾼 시각
 */
public record CrewLeaderChangedEvent(
	String eventId,
	long crewId,
	long previousLeaderId,
	long newLeaderId,
	LocalDateTime changedAt
) {
}
