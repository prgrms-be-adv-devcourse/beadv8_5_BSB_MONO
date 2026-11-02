package com.bukang.payout.dto;

import java.time.LocalDateTime;

import com.bukang.payout.domain.PayoutRecovery;
import com.bukang.payout.domain.PayoutRecoveryReason;
import com.bukang.payout.domain.PayoutRecoveryStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class PayoutRecoveryDto {
	private final long id;
	private final long sellerId;
	private final long raceId;
	private final PayoutRecoveryReason reasonType;
	private final long amount;
	private final PayoutRecoveryStatus status;
	private final LocalDateTime requestedAt;
	// 요청 중에는 null
	private final Long completedBy;
	private final LocalDateTime completedAt;

	public static PayoutRecoveryDto from(PayoutRecovery recovery) {
		return new PayoutRecoveryDto(
			recovery.getId(),
			recovery.getSellerId(),
			recovery.getRaceId(),
			recovery.getReasonType(),
			recovery.getAmount(),
			recovery.getStatus(),
			recovery.getRequestedAt(),
			recovery.getCompletedBy(),
			recovery.getCompletedAt()
		);
	}
}
