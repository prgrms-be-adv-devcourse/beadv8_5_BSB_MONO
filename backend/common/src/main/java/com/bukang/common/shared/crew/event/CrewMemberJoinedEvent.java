package com.bukang.common.shared.crew.event;

import java.time.LocalDateTime;

/**
 * 회원이 초대 링크로 크루에 가입했다 (BUK-99). 회원 서비스가 크루원 행을 만든 트랜잭션에서 아웃박스로 남기고, 커밋 뒤 보낸다.
 * 크루를 만든 크루장은 가입이 아니므로 보내지 않는다.
 * 크루 ID를 key로 보내 같은 크루의 이벤트는 순서대로 간다. 이메일·실명·휴대폰 같은 개인정보는 담지 않는다
 *
 * @param eventId 받는 쪽이 같은 이벤트를 두 번 처리하지 않게 거르는 값 (UUID, Kafka 헤더 eventId와 같다)
 * @param joinedAt 크루원 행의 가입 시각. 크루원 이탈 이벤트의 joinedAt과 같은 값이다
 */
public record CrewMemberJoinedEvent(
	String eventId,
	long crewId,
	long memberId,
	LocalDateTime joinedAt
) {
}
