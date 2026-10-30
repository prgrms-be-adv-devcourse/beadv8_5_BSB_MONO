package com.bukang.member.crew.dto;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "만든 초대 링크. inviteToken은 이 응답에서만 받는다")
public record CrewInviteCreatedDto(
	long inviteId,
	String inviteToken,
	LocalDateTime createdAt,
	LocalDateTime expiresAt
) {
}
