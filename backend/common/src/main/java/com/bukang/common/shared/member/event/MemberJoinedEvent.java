package com.bukang.common.shared.member.event;

import java.time.LocalDateTime;

/**
 * 회원 가입 이벤트 (member-service가 발행하고, 다른 서비스가 받아 ReplicaMember로 복제한다)
 * 브로커에 평문 JSON으로 남으므로 BaseMember와 같은 공개 정보만 담는다. 이메일, 휴대폰 번호는 넣지 않는다.
 */
public record MemberJoinedEvent(
	int id,
	LocalDateTime createDate,
	LocalDateTime modifyDate,
	String nickname
) {
}
