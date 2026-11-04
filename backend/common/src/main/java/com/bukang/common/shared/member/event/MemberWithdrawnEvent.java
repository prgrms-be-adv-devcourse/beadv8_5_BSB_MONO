package com.bukang.common.shared.member.event;

import java.time.LocalDateTime;

/**
 * 회원 탈퇴 이벤트. 개인정보는 지운 뒤라 회원 ID와 탈퇴 시각만 담는다
 */
public record MemberWithdrawnEvent(
	String eventId,
	long id,
	LocalDateTime withdrawnAt
) {
}
