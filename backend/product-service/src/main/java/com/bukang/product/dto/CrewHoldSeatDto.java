package com.bukang.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

// 크루 자리 확보 요청의 자리 1석 (파티원 1명의 참가 신청)
@Schema(description = "잡을 크루 자리 1석")
@AllArgsConstructor
@Getter
public class CrewHoldSeatDto {
	@Schema(description = "주문 서비스의 참가 신청 ID (자리 1석 = 참가 신청 1건)", example = "1001")
	@NotNull(message = "참가 신청 ID를 작성해주세요.")
	@Positive(message = "참가 신청 ID는 0보다 커야 합니다.")
	private final Long participationId;

	@Schema(description = "종목 ID", example = "10")
	@NotNull(message = "종목 ID를 작성해주세요.")
	@Positive(message = "종목 ID는 0보다 커야 합니다.")
	private final Long raceCourseId;
}
