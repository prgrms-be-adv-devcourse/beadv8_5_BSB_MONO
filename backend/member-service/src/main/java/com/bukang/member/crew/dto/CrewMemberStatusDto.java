package com.bukang.member.crew.dto;

import java.time.LocalDateTime;

import com.bukang.member.crew.domain.CrewRole;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 크루원 여부 (내부 API). 크루원이 아니면 role·joinedAt이 null이다
 *
 * @param crewMember 지금 이 크루의 크루원이면 true
 * @param role       크루 역할 (LEADER: 크루장, MANAGER: 운영진, MEMBER: 크루원)
 * @param joinedAt   이 크루에 가입한 시각. 크루를 만든 크루장은 크루를 만든 시각
 */
@Schema(description = "크루원 여부 (내부 API)")
public record CrewMemberStatusDto(
	long crewId,
	long memberId,
	boolean crewMember,
	CrewRole role,
	LocalDateTime joinedAt
) {
}
