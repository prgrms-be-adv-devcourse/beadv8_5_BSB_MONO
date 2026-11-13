package com.bukang.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "크루런 AI 질문 요청")
public record AiChatRequestDto(
	@Schema(description = "질문 (1,000자 이하)", example = "송장 입력 대기 키트는 몇 개예요?")
	@NotBlank(message = "질문을 입력해주세요.")
	@Size(max = 1000, message = "질문은 1,000자 이하로 입력해주세요.")
	String message
) {
}
