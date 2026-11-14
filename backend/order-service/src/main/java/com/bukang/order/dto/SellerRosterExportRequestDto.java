package com.bukang.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Schema(description = "참가자 명단 내려받기 요청. 내려받는 사유를 남긴다")
@AllArgsConstructor
@Getter
public class SellerRosterExportRequestDto {
	@Schema(description = "대회 ID", example = "1")
	@NotNull(message = "대회를 골라주세요.")
	private final Long raceId;

	@Schema(description = "종목 ID (생략하면 대회의 모든 종목)", example = "1")
	private final Long raceCourseId;

	@Schema(description = "내려받는 사유 (200자 이하)", example = "대회 당일 현장 접수 확인")
	@NotBlank(message = "내려받는 사유를 입력해주세요.")
	@Size(max = 200, message = "사유는 200자 이하로 입력해주세요.")
	private final String reason;
}
