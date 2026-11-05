package com.bukang.member.dto;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "광고 수신 거부 링크")
public record MarketingUnsubscribeLinkDto(
	@Schema(description = "메시지 끝에 붙일 링크 (프론트 확인 화면)",
		example = "https://crewrun.example/unsubscribe?token=7.1793750400.q1b2c3...")
	String url,
	@Schema(description = "링크를 쓸 수 있는 마지막 시각", example = "2027-11-04T14:30:00")
	LocalDateTime expiresAt
) {
}
