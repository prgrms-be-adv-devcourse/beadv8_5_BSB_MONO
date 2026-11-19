package com.bukang.member.crew.dto;

import java.time.LocalDateTime;

import com.bukang.member.crew.domain.Crew;
import com.bukang.member.crew.domain.CrewInvite;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "초대 받기 화면 정보")
public record CrewInvitePreviewDto(
	long crewId,
	String crewName,
	int memberCount,
	LocalDateTime expiresAt,
	@Schema(description = "요청한 회원이 이미 이 크루의 크루원인지") boolean alreadyMember
) {
	public static CrewInvitePreviewDto of(Crew crew, int memberCount, CrewInvite invite, boolean alreadyMember) {
		return new CrewInvitePreviewDto(crew.getId(), crew.getName(), memberCount, invite.getExpiresAt(),
			alreadyMember);
	}
}
