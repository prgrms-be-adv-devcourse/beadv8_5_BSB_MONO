package com.bukang.common.shared.member.event;

import java.time.LocalDateTime;

public record MemberModifiedEvent(
	long id,
	LocalDateTime createDate,
	LocalDateTime modifyDate,
	String nickname,
	String eventId
) {
	// 받는 쪽 초기 데이터·테스트 전용. 회원 서비스는 쓰지 않는다 (eventId가 비어 아웃박스 저장에서 예외가 난다)
	public MemberModifiedEvent(long id, LocalDateTime createDate, LocalDateTime modifyDate, String nickname) {
		this(id, createDate, modifyDate, nickname, null);
	}
}
