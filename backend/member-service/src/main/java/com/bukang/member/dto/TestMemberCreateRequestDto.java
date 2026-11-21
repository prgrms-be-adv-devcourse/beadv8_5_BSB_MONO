package com.bukang.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(description = "테스트 회원 만들기 요청")
public record TestMemberCreateRequestDto(
	@Schema(description = "만들 회원 수 (1~20)", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
	@Min(value = 1, message = "1명 이상 만들어야 합니다.")
	@Max(value = 20, message = "한 번에 20명까지 만들 수 있습니다.")
	int count
) {
}
