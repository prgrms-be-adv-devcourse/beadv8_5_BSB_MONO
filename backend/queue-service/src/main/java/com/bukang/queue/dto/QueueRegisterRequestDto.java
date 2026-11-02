package com.bukang.queue.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Schema(description = "대기열 등록 요청")
@AllArgsConstructor
@Getter
public class QueueRegisterRequestDto {
	@Schema(description = "대회 ID", example = "1")
	@NotNull(message = "대회 ID를 작성해주세요.")
	@Positive(message = "대회 ID는 0보다 커야 합니다.")
	private final Long raceId;

	@Schema(description = "종목 ID", example = "1")
	@NotNull(message = "종목 ID를 작성해주세요.")
	@Positive(message = "종목 ID는 0보다 커야 합니다.")
	private final Long raceCourseId;
}
