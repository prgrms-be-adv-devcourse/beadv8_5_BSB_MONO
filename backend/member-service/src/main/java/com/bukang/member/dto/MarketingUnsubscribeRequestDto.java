package com.bukang.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

// 확인 화면이 주소의 token을 그대로 보낸다 (BUK-75)
@Schema(description = "광고 수신 거부 요청")
public record MarketingUnsubscribeRequestDto(
	@Schema(description = "수신 거부 링크의 token 값", example = "7.1793750400.q1b2c3...")
	@NotBlank(message = "수신 거부 토큰을 보내주세요.")
	String token
) {
}
