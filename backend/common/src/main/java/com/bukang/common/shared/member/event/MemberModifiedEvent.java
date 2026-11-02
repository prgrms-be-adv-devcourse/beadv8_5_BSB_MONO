package com.bukang.common.shared.member.event;

import java.time.LocalDateTime;

public record MemberModifiedEvent(
	long id,
	LocalDateTime createDate,
	LocalDateTime modifyDate,
	String nickname
) {
}
