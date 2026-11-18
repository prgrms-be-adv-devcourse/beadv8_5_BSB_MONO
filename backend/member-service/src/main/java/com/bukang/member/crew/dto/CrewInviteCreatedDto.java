package com.bukang.member.crew.dto;

import java.time.LocalDateTime;

import com.bukang.member.crew.domain.CrewInvite;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "만든 초대 링크")
public record CrewInviteCreatedDto(
	long inviteId,
	String inviteToken,
	LocalDateTime createdAt,
	LocalDateTime expiresAt
) {
	public static CrewInviteCreatedDto of(CrewInvite invite) {
		return new CrewInviteCreatedDto(invite.getId(), invite.getInviteToken(), invite.getCreatedAt(),
			invite.getExpiresAt());
	}
}
