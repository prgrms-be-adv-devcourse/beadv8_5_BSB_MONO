package com.bukang.member.crew.dto;

import java.time.LocalDateTime;

import com.bukang.member.crew.domain.CrewRole;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "초대로 가입한 결과")
public record CrewJoinedDto(
	long crewId,
	String crewName,
	@Schema(description = "크루 역할 (LEADER: 크루장, MANAGER: 운영진, MEMBER: 크루원)") CrewRole role,
	LocalDateTime joinedAt
) {
}
