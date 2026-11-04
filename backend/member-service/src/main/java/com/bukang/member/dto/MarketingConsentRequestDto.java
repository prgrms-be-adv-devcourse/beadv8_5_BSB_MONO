package com.bukang.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

// 토글이 바뀐 뒤의 값을 그대로 보낸다. 빠뜨리면 켜짐·꺼짐을 알 수 없으므로 필수다
@Schema(description = "마케팅 수신 동의 변경 요청")
public record MarketingConsentRequestDto(
	@Schema(description = "동의하면 true, 거부하면 false", example = "false")
	@NotNull(message = "동의 여부를 보내주세요.")
	Boolean agreed
) {
}
