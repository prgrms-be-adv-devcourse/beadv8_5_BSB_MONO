package com.bukang.payout.dto;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

// 관리자 정산 · 환급 내역 화면 응답 (BUK-168). 판매자 이름 · 엑셀은 프론트가 맡는다
@Schema(description = "관리자 정산 목록")
public record PayoutAdminPayoutsDto(
	@Schema(description = "조회한 지급 예정일", example = "2026-11-20")
	LocalDate payDate,
	@Schema(description = "카드: 그날의 상태별 건수 · 지급액 합. 모든 상태가 0건이어도 나오고, 상태 선택과 상관없다")
	List<PayoutStatusSummaryDto> summary,
	PageDto<PayoutAdminPayoutDto> payouts
) {
}
