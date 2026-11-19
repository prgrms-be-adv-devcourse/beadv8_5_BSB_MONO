package com.bukang.payout.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Schema(description = "건별 정산 CSV 내려받기 요청. 정산 생성일 기간을 받는다(조회 API payout-items와 같은 조건)")
@AllArgsConstructor
@Getter
public class PayoutItemExportRequestDto {
	@Schema(description = "조회 시작 정산 생성일 (yyyy-MM-dd)", example = "2026-09-01")
	@NotNull(message = "필수 요청 값이 없습니다.")
	@JsonFormat(pattern = "yyyy-MM-dd")
	private final LocalDate from;

	@Schema(description = "조회 끝 정산 생성일 (yyyy-MM-dd, 포함). 시작 날 + 1개월 전까지(최대 1개월)", example = "2026-09-30")
	@NotNull(message = "필수 요청 값이 없습니다.")
	@JsonFormat(pattern = "yyyy-MM-dd")
	private final LocalDate to;
}
