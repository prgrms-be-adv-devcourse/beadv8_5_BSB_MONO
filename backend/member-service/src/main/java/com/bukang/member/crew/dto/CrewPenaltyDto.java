package com.bukang.member.crew.dto;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "크루 파티 만들기 제한")
public record CrewPenaltyDto(
	@Schema(description = "지금 파티를 만들 수 없으면 true") boolean restricted,
	@Schema(description = "제한이 끝나는 시각. 기간이 겹치면 가장 늦게 끝나는 시각. 제한 중이 아니면 null") LocalDateTime endAt
) {
	public static CrewPenaltyDto of(LocalDateTime restrictedUntil) {
		return new CrewPenaltyDto(restrictedUntil != null, restrictedUntil);
	}
}
