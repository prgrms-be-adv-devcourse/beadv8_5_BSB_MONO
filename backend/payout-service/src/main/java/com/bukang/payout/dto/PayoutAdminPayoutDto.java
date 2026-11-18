package com.bukang.payout.dto;

import java.util.List;

import com.bukang.payout.domain.Payout;

import io.swagger.v3.oas.annotations.media.Schema;

// 관리자 정산 목록 한 줄: 정산 내역 + 판매자 ID + 진행 중 보류 (BUK-168)
@Schema(description = "관리자 정산 목록 한 줄")
public record PayoutAdminPayoutDto(
	@Schema(description = "정산 내역 (판매자 정산 조회와 같은 모양)")
	PayoutDto payout,
	@Schema(description = "판매자 ID", example = "1")
	long sellerId,
	@Schema(description = "진행 중인 지급보류. id로 보류 해제를 부른다. 없으면 빈 목록")
	List<PayoutHoldDto> holds
) {
	public static PayoutAdminPayoutDto of(Payout payout, List<PayoutHoldDto> holds) {
		return new PayoutAdminPayoutDto(PayoutDto.from(payout), payout.getSellerId(), holds);
	}
}
