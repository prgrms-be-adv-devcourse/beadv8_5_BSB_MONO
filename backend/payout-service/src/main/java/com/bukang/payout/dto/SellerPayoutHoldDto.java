package com.bukang.payout.dto;

import java.time.LocalDateTime;

import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutHold;
import com.bukang.payout.domain.PayoutHoldReason;
import com.bukang.payout.domain.PayoutHoldStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 판매자에게 보여 주는 지급보류 (지급보류 내역 탭의 한 줄)
 * 관리자용 PayoutHoldDto와 나눈 이유: 보류 · 해제한 관리자 회원 ID는 판매자에게 보여 줄 정보가 아니고,
 * 판매자 화면에는 보류된 정산 내역의 정산 ID · 지급액이 필요하다
 */
@AllArgsConstructor
@Getter
public class SellerPayoutHoldDto {
	private final long id;
	private final String holdCode;
	private final long payoutId;
	private final String payoutCode;
	private final long raceId;
	private final String raceName;
	private final long payoutAmount;
	// 판매자 화면에 사유를 보여 준다 (payout.md 1장 지급보류 2번)
	private final PayoutHoldReason reasonType;
	private final String reason;
	private final PayoutHoldStatus status;
	private final LocalDateTime heldAt;
	// 보류 중에는 null
	private final LocalDateTime releasedAt;

	// 정산 내역(payout)을 join fetch로 함께 읽은 보류만 넘긴다
	public static SellerPayoutHoldDto from(PayoutHold hold) {
		Payout payout = hold.getPayout();
		return new SellerPayoutHoldDto(
			hold.getId(),
			hold.getHoldCode(),
			payout.getId(),
			payout.getPayoutCode(),
			payout.getRaceId(),
			payout.getRaceName(),
			payout.getPayoutAmount(),
			hold.getReasonType(),
			hold.getReason(),
			hold.getStatus(),
			hold.getHeldAt(),
			hold.getReleasedAt()
		);
	}
}
