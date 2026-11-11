package com.bukang.payout.dto;

import java.time.LocalDateTime;

import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutRecovery;
import com.bukang.payout.domain.PayoutRecoveryReason;
import com.bukang.payout.domain.PayoutRecoveryStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 판매자 회수 내역 탭(S04-8)의 한 줄: 청구에 들어간 회수 한 건 (BUK-106)
 * 청구는 판매자 x 대회 하나라 같은 대회의 줄은 청구 ID가 같다
 */
@AllArgsConstructor
@Getter
public class SellerRecoveryDto {
	private final long id;
	private final String claimCode;
	private final long raceId;
	// 그 대회의 정산 내역이 없으면 null
	private final String raceName;
	private final String payoutCode;
	private final long amount;
	private final PayoutRecoveryReason reasonType;
	private final PayoutRecoveryStatus status;
	private final LocalDateTime requestedAt;
	// 입금 기다리는 중에는 null
	private final LocalDateTime completedAt;

	// payout: 그 대회의 최종 정산(없으면 1차 정산). 둘 다 없으면 null
	public static SellerRecoveryDto of(PayoutRecovery recovery, String claimCode, Payout payout) {
		return new SellerRecoveryDto(
			recovery.getId(),
			claimCode,
			recovery.getRaceId(),
			payout == null ? null : payout.getRaceName(),
			payout == null ? null : payout.getPayoutCode(),
			recovery.getAmount(),
			recovery.getReasonType(),
			recovery.getStatus(),
			recovery.getRequestedAt(),
			recovery.getCompletedAt()
		);
	}
}
