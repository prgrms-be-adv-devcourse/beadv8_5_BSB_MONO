package com.bukang.member.crew.dto;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "아직 쓸 수 있는 초대")
public record CrewInviteDto(
	long inviteId,
	CrewInviteSenderDto createdBy,
	LocalDateTime createdAt,
	LocalDateTime expiresAt
) {
}
