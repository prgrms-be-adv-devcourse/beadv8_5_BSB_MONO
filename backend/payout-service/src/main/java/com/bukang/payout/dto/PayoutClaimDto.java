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
	// 청구 ID 표기 (RC-만든 날-16진수 4자리)
	private final String claimCode;
	private final long sellerId;
	private final long raceId;
	private final long amount;
	private final LocalDate startDate;
	// 판매자 입금 기한 (상계 실패일 + 30일)
	private final LocalDate dueDate;
	private final PayoutClaimStatus status;
	// 입금 확인 전에는 null
	private final Long confirmedBy;
	private final LocalDateTime confirmedAt;

	public static PayoutClaimDto from(PayoutClaim claim) {
		return new PayoutClaimDto(
			claim.getId(),
			claim.getClaimCode(),
			claim.getSellerId(),
			claim.getRaceId(),
			claim.getAmount(),
			claim.getStartDate(),
			claim.getDueDate(),
			claim.getStatus(),
			claim.getConfirmedBy(),
			claim.getConfirmedAt()
		);
	}
}
