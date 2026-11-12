package com.bukang.payout.dto;

import com.bukang.payout.domain.PayoutPayCheckResult;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 지급 확인 필요 닫기 요청 (BUK-174). 메모는 상태 이력 사유 칸(255자)에 그대로 남는다
@Schema(description = "지급 확인 필요 정산 내역 닫기 요청")
public record PayoutPayCheckRequestDto(
	@Schema(description = "캐시 서비스에서 확인한 지급 결과 (지급됨: 지급 완료로, 지급_안_됨: 지급 예정으로 되돌려 다시 지급)",
		example = "지급됨")
	@NotNull(message = "지급 결과를 선택해주세요.")
	PayoutPayCheckResult result,

	@Schema(description = "판단 근거 메모 (255자 이하)", example = "캐시 정산 이동 기록에서 대금 · 수수료 이동 확인")
	@NotBlank(message = "판단 근거 메모를 작성해주세요.")
	@Size(max = 255, message = "판단 근거 메모는 255자 이하로 작성해주세요.")
	String memo
) {
}
