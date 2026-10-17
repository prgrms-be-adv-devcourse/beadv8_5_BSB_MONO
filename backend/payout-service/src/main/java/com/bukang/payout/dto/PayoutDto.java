package com.bukang.payout.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutStage;
import com.bukang.payout.domain.PayoutStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 판매자에게 보여 주는 정산 내역 (월별 정산 탭의 한 줄)
 * 대회 이름은 정산 내역을 만들 때 저장한 값이다 (정산한 때의 이름)
 */
@AllArgsConstructor
@Getter
public class PayoutDto {
	private final int id;
	private final String payoutCode;
	private final int raceId;
	private final String raceName;
	// 정산 단계: FIRST(1차 정산금, 40%) / FINAL(최종). 같은 대회 · 같은 달에 두 줄이 생길 수 있다
	private final PayoutStage payoutStage;
	private final String payoutMonth;
	private final long salesAmount;
	private final long refundAmount;
	private final long feeAmount;
	// 1차 정산금 차감: 최종에서 뺀 1차 지급액 − 회수액. 1차는 0이다
	private final long advanceDeductionAmount;
	private final long payoutAmount;
	private final PayoutStatus status;
	private final LocalDateTime settledAt;
	// 지급 예정일. 보류를 풀면 해제일 뒤 돌아오는 지급일로 바뀐다
	private final LocalDate scheduledPayDate;
	// 지급 완료 전에는 null
	private final LocalDateTime paidAt;

	public static PayoutDto from(Payout payout) {
		return new PayoutDto(
			payout.getId(),
			payout.getPayoutCode(),
			payout.getRaceId(),
			payout.getRaceName(),
			payout.getPayoutStage(),
			payout.getPayoutMonth(),
			payout.getSalesAmount(),
			payout.getRefundAmount(),
			payout.getFeeAmount(),
			payout.getAdvanceDeductionAmount(),
			payout.getPayoutAmount(),
			payout.getStatus(),
			payout.getSettledAt(),
			payout.getScheduledPayDate(),
			payout.getPaidAt()
		);
	}
}
