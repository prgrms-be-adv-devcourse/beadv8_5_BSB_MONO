package com.bukang.common.shared.member.event;

import java.time.LocalDateTime;

/**
 * 회원 가입 이벤트 (member-service가 발행하고, 다른 서비스가 받아 ReplicaMember로 복제한다)
 * 브로커에 평문 JSON으로 남으므로 BaseMember와 같은 공개 정보만 담는다. 이메일, 휴대폰 번호는 넣지 않는다.
 * eventId는 Kafka 헤더 eventId와 같은 값이다 (받는 쪽 중복 거르기용)
 */
public record MemberJoinedEvent(
	long id,
	LocalDateTime createDate,
	LocalDateTime modifyDate,
	String nickname,
	boolean marketingAgreed,
	String eventId
) {
	// 받는 쪽 초기 데이터·테스트 전용. 회원 서비스는 쓰지 않는다 (eventId가 비어 아웃박스 저장에서 예외가 난다)
	public MemberJoinedEvent(long id, LocalDateTime createDate, LocalDateTime modifyDate, String nickname) {
		this(id, createDate, modifyDate, nickname, false, null);
	}
}
