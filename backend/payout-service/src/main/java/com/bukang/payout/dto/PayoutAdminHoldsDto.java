package com.bukang.payout.dto;

import io.swagger.v3.oas.annotations.media.Schema;

// 관리자 지급보류 관리 화면 응답 (BUK-168). 판매자 이름 · 엑셀은 프론트가 맡는다
@Schema(description = "관리자 지급보류 목록")
public record PayoutAdminHoldsDto(
	PayoutHoldSummaryDto summary,
	PageDto<PayoutAdminHoldDto> holds
) {
}
