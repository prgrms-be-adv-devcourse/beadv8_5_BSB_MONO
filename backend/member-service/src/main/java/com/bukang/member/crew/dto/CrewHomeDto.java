package com.bukang.member.crew.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.bukang.member.crew.domain.CrewRole;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "크루 홈")
public record CrewHomeDto(
	long crewId,
	String name,
	LocalDateTime createdAt,
	@Schema(description = "요청한 회원의 역할. 크루 역할 (LEADER: 크루장, MANAGER: 운영진, MEMBER: 크루원)") CrewRole myRole,
	int memberCount,
	List<CrewMemberDto> members
) {
}
