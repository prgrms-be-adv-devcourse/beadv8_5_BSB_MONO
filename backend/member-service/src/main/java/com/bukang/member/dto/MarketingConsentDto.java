package com.bukang.member.dto;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "마케팅 수신 동의 상태")
public record MarketingConsentDto(
	@Schema(description = "마케팅 정보 수신에 동의했으면 true", example = "true")
	boolean agreed,
	@Schema(description = "마지막으로 동의·거부를 정한 시각. 기록이 없으면 null", example = "2026-11-04T14:30:00")
	LocalDateTime decidedAt
) {
}
