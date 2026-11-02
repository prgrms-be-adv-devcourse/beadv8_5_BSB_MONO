package com.bukang.payout.dto;

import java.time.LocalDateTime;

import com.bukang.payout.domain.PayoutHold;
import com.bukang.payout.domain.PayoutHoldReason;
import com.bukang.payout.domain.PayoutHoldStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class PayoutHoldDto {
	private final long id;
	private final String holdCode;
	private final long payoutId;
	private final PayoutHoldReason reasonType;
	private final String reason;
	private final PayoutHoldStatus status;
	private final long heldBy;
	private final LocalDateTime heldAt;
	// 보류 중에는 null
	private final Long releasedBy;
	private final LocalDateTime releasedAt;

	public static PayoutHoldDto from(PayoutHold hold) {
		return new PayoutHoldDto(
			hold.getId(),
			hold.getHoldCode(),
			hold.getPayout().getId(),
			hold.getReasonType(),
			hold.getReason(),
			hold.getStatus(),
			hold.getHeldBy(),
			hold.getHeldAt(),
			hold.getReleasedBy(),
			hold.getReleasedAt()
		);
	}
}
