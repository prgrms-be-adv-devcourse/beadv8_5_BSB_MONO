package com.bukang.order.dto;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

// 크루가 파티 만들기 제한 중일 때(403 ORDER_CREW_PARTY_RESTRICTED)의 data
public record CrewPartyRestrictedDto(
	@Schema(description = "제한이 끝나는 시각. 이때부터 다시 파티를 만들 수 있다", example = "2026-12-12T04:30:00")
	LocalDateTime endAt
) {
}
