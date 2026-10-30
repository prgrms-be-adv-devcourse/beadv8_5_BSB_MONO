package com.bukang.member.crew.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "크루원 수 (내부 API). 크루장, 운영진, 크루원을 모두 센다")
public record CrewMemberCountDto(long crewId, int memberCount) {
}
