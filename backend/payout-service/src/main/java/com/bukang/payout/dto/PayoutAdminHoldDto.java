package com.bukang.payout.dto;

import java.time.LocalDateTime;

import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutHold;
import com.bukang.payout.domain.PayoutHoldReason;
import com.bukang.payout.domain.PayoutHoldStatus;

import io.swagger.v3.oas.annotations.media.Schema;

// 관리자 지급보류 목록 한 줄 (BUK-168). 정산은 조회할 때 함께 읽어 둔다(join fetch)
@Schema(description = "관리자 지급보류 목록 한 줄")
public record PayoutAdminHoldDto(
	@Schema(description = "지급보류 ID (보류 해제 API 경로에 쓴다)", example = "2")
	long id,
	@Schema(description = "지급보류 ID 표기", example = "HD-20261025-0C13")
	String holdCode,
	@Schema(description = "판매자 ID", example = "1")
	long sellerId,
	@Schema(description = "정산 내역 ID", example = "7")
	long payoutId,
	@Schema(description = "정산 ID 표기", example = "ST-20261018-0007")
	String payoutCode,
	@Schema(description = "보류된 정산의 지급액", example = "2400000")
	long payoutAmount,
	PayoutHoldReason reasonType,
	@Schema(description = "사유 상세", example = "계좌 확인 중")
	String reason,
	PayoutHoldStatus status,
	LocalDateTime heldAt,
	@Schema(description = "해제 일시 (보류 중에는 null)")
	LocalDateTime releasedAt
) {
	public static PayoutAdminHoldDto from(PayoutHold hold) {
		Payout payout = hold.getPayout();
		return new PayoutAdminHoldDto(hold.getId(), hold.getHoldCode(), payout.getSellerId(), payout.getId(),
			payout.getPayoutCode(), payout.getPayoutAmount(), hold.getReasonType(), hold.getReason(), hold.getStatus(),
			hold.getHeldAt(), hold.getReleasedAt());
	}
}
