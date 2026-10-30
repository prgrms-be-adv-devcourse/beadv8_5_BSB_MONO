package com.bukang.member.crew.dto;

import com.bukang.member.crew.domain.CrewRole;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "내 크루 목록의 크루 하나")
public record MyCrewDto(
	long crewId,
	String name,
	@Schema(description = "크루 역할 (LEADER: 크루장, MANAGER: 운영진, MEMBER: 크루원)") CrewRole myRole,
	int memberCount
) {
}
