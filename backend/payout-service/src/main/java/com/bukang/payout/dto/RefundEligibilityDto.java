package com.bukang.payout.dto;

import java.time.LocalDate;

import com.bukang.payout.domain.PayoutCreateDate;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 참가권 환불 가능 여부. 주문 서비스가 환불 요청을 받을 때 물어본다 (PRO-28)
 * 판정 기준(정산 생성일)을 함께 돌려줘서, 거절할 때 언제부터 막혔는지 안내할 수 있게 한다
 */
@AllArgsConstructor
@Getter
public class RefundEligibilityDto {
	private final LocalDate raceDate;
	private final LocalDate payoutTargetDate;
	private final boolean refundable;

	public static RefundEligibilityDto of(LocalDate raceDate, LocalDate today) {
		return new RefundEligibilityDto(
			raceDate,
			PayoutCreateDate.of(raceDate),
			PayoutCreateDate.isRefundable(raceDate, today)
		);
	}
}
