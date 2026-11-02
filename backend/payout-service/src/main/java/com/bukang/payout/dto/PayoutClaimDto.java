package com.bukang.payout.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.bukang.payout.domain.PayoutClaim;
import com.bukang.payout.domain.PayoutClaimStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class PayoutClaimDto {
	private final long id;
	private final long sellerId;
	private final long raceId;
	private final long amount;
	private final LocalDate startDate;
	private final PayoutClaimStatus status;
	// 입금 확인 전에는 null
	private final Long confirmedBy;
	private final LocalDateTime confirmedAt;

	public static PayoutClaimDto from(PayoutClaim claim) {
		return new PayoutClaimDto(
			claim.getId(),
			claim.getSellerId(),
			claim.getRaceId(),
			claim.getAmount(),
			claim.getStartDate(),
			claim.getStatus(),
			claim.getConfirmedBy(),
			claim.getConfirmedAt()
		);
	}
}
