package com.bukang.member.crew.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 칸이 하나라 기본 생성자 + 필드로 받는다 (CrewCreateRequestDto 참고)
@Schema(description = "크루장 넘기기 요청")
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class CrewLeaderTransferRequestDto {
	@Schema(description = "크루장을 넘겨받을 크루원의 회원 ID", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull(message = "크루장을 넘겨받을 크루원을 선택해주세요.")
	private Long memberId;
}
