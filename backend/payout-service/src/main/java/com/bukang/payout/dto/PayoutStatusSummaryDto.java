package com.bukang.payout.dto;

import com.bukang.payout.domain.PayoutStatus;

import io.swagger.v3.oas.annotations.media.Schema;

// 관리자 정산 목록 카드 한 칸: 지급 예정일 하루의 한 상태 (BUK-168)
@Schema(description = "정산 상태별 건수 · 지급액 합")
public record PayoutStatusSummaryDto(
	PayoutStatus status,
	@Schema(description = "그 상태의 정산 수", example = "12")
	long count,
	@Schema(description = "그 상태의 지급액 합", example = "8600000")
	long payoutAmount
) {
}
