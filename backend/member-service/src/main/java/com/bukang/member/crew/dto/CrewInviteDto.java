package com.bukang.member.crew.dto;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "아직 쓸 수 있는 초대")
public record CrewInviteDto(
	long inviteId,
	@Schema(description = "초대 링크 토큰. 원문을 저장하기 전(BUK-195)에 만든 초대는 null",
		example = "q3Xv9kLm2NpR7tYw4ZaB1c", nullable = true)
	String inviteToken,
	CrewInviteSenderDto createdBy,
	LocalDateTime createdAt,
	LocalDateTime expiresAt
) {
}
