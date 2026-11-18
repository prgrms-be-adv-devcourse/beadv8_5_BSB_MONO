package com.bukang.payout.dto;

import io.swagger.v3.oas.annotations.media.Schema;

// 관리자 지급보류 화면의 카드 숫자. 목록 필터와 상관없이 전체 보류 기준이다 (BUK-168)
@Schema(description = "지급보류 카드 숫자")
public record PayoutHoldSummaryDto(
	@Schema(description = "보류 중인 지급보류 수", example = "2")
	long heldCount,
	@Schema(description = "보류 중인 정산의 지급액 합. 보류가 여럿 걸린 정산도 한 번만 더한다", example = "3260000")
	long heldAmount,
	@Schema(description = "이번 달(한국 시간)에 푼 지급보류 수", example = "1")
	long releasedThisMonthCount
) {
}
