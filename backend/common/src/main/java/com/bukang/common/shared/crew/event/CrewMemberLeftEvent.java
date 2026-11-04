package com.bukang.common.shared.crew.event;

import java.time.LocalDateTime;

/**
 * 크루원이 크루에서 빠졌다 (BE-032). 회원 서비스가 크루원 행을 지운 트랜잭션에서 아웃박스로 남기고, 커밋 뒤 보낸다.
 * 주문 서비스가 받아 그 크루의 파티에서 그 사람을 빼고 결제하지 않은 임시 자리를 푼다.
 * 크루 ID를 key로 보내 같은 크루의 이벤트는 순서대로 간다. 이메일·실명·휴대폰 같은 개인정보는 담지 않는다
 *
 * @param eventId 받는 쪽이 같은 이벤트를 두 번 처리하지 않게 거르는 값 (UUID, Kafka 헤더 eventId와 같다)
 * @param leftAt 크루원 행을 지운 시각
 */
public record CrewMemberLeftEvent(
	String eventId,
	long crewId,
	long memberId,
	CrewMemberLeftReason reason,
	LocalDateTime leftAt
) {
}
