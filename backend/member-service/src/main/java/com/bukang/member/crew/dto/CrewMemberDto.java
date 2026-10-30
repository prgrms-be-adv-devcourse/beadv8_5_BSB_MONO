package com.bukang.member.crew.dto;

import com.bukang.member.crew.domain.CrewRole;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "크루원 (실명 없이 닉네임만)")
public record CrewMemberDto(
	@Schema(example = "1") long memberId,
	@Schema(example = "새벽러너") String nickname,
	@Schema(description = "크루 역할 (LEADER: 크루장, MANAGER: 운영진, MEMBER: 크루원)") CrewRole role
) {
}
