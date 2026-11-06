package com.bukang.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 토큰을 주소(쿼리)에 넣으면 게이트웨이·접근 로그에 남으므로 본문으로 받는다 (BUK-39)
public record PartyPreviewRequestDto(
	@Schema(description = "참가 링크 토큰", example = "k3Jx9dQ2mV7pL0sA8fB1nC4eR6tY5uI2oP9wZ3xH7gK")
	@NotBlank(message = "참가 링크가 없습니다.")
	@Size(max = 64, message = "참가 링크가 올바르지 않습니다.") String joinToken
) {
}
