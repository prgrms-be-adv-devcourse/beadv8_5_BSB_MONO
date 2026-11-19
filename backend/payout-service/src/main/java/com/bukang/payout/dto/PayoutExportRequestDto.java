package com.bukang.payout.dto;

import java.time.YearMonth;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Schema(description = "정산 내역 CSV 내려받기 요청. 정산 월 기간을 받는다(조회 API payouts와 같은 조건)")
@AllArgsConstructor
@Getter
public class PayoutExportRequestDto {
	@Schema(description = "조회 시작 정산 월 (yyyy-MM)", example = "2025-11")
	@NotNull(message = "필수 요청 값이 없습니다.")
	@JsonFormat(pattern = "yyyy-MM")
	private final YearMonth fromMonth;

	@Schema(description = "조회 끝 정산 월 (yyyy-MM, 포함). 시작 달 포함 최대 12개월", example = "2026-10")
	@NotNull(message = "필수 요청 값이 없습니다.")
	@JsonFormat(pattern = "yyyy-MM")
	private final YearMonth toMonth;
}
