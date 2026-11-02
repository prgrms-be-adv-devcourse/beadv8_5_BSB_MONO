package com.bukang.product.dto;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

// 주문 서비스가 [결제하기] 때 보내는 개인 자리 1석 확보 요청
@Schema(description = "개인 자리 확보 요청 (주문 서비스가 [결제하기] 때 보낸다)")
@AllArgsConstructor
@Getter
public class HoldRequestDto {
	@Schema(description = "종목 ID", example = "1")
	@NotNull(message = "종목 ID를 작성해주세요.")
	@Positive(message = "종목 ID는 0보다 커야 합니다.")
	private final Long raceCourseId;

	// 같은 신청으로 다시 요청해도 자리는 한 번만 잡힌다 (멱등 키)
	@Schema(description = "주문 서비스의 참가 신청 ID (같은 값으로 다시 요청하면 처음 결과를 돌려준다)", example = "1001")
	@NotNull(message = "참가 신청 ID를 작성해주세요.")
	@Positive(message = "참가 신청 ID는 0보다 커야 합니다.")
	private final Long participationId;

	// 대기열을 통과한 뒤 받은 활성 시간(10분)이 끝나는 시각. 이 시각까지만 자리를 유지한다
	@Schema(description = "임시 확보 끝 시각 (대기열 활성 시간이 끝나는 시각, 지금보다 늦어야 한다)",
		example = "2026-10-13T10:10:00")
	@NotNull(message = "임시 확보 끝 시각을 작성해주세요.")
	private final LocalDateTime expiresAt;
}
