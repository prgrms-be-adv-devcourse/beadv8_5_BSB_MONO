package com.bukang.member.crew.dto;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "크루원 수 (내부 API). 크루장, 운영진, 크루원을 모두 센다. 크루 생성 시각도 함께 준다")
public record CrewMemberCountDto(long crewId, int memberCount, LocalDateTime crewCreatedAt) {
}
