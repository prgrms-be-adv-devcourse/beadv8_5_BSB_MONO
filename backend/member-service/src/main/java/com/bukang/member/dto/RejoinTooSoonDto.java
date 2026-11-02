package com.bukang.member.dto;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "탈퇴 후 재가입 제한 오류의 data. 화면이 다시 가입할 수 있는 날을 보여 준다")
public record RejoinTooSoonDto(
	@Schema(description = "다시 가입할 수 있는 날", example = "2026-12-02")
	LocalDate rejoinableDate,
	@Schema(description = "탈퇴하고 다시 가입할 수 있기까지의 날수", example = "30")
	int blockDays
) {
}
