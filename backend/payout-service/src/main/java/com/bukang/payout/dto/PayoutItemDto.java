package com.bukang.payout.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutItem;
import com.bukang.payout.domain.PayoutRecordType;
import com.bukang.payout.domain.PayoutStatus;
import com.bukang.payout.domain.TwoStagePayoutCalculator;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 판매자에게 보여 주는 건별 정산 (참가권 1장)
 * 어느 정산 내역에 묶였고 언제 지급되는지 함께 보여 주려고 정산 내역의 ID · 상태 · 지급 예정일을 붙인다
 */
@AllArgsConstructor
@Getter
public class PayoutItemDto {
	private final int id;
	private final Integer orderItemId;
	private final PayoutRecordType recordType;
	private final long amount;
	private final BigDecimal feeRate;
	private final long feeAmount;
	private final LocalDate targetDate;
	private final int payoutId;
	private final String payoutCode;
	private final int raceId;
	private final String raceName;
	private final PayoutStatus payoutStatus;
	private final LocalDate scheduledPayDate;

	// 정산 내역(payout)을 join fetch로 함께 읽은 항목만 넘긴다. 트랜잭션 밖에서 LAZY를 읽지 않도록 안에서 바꾼다
	public static PayoutItemDto from(PayoutItem item) {
		Payout payout = item.getPayout();
		return new PayoutItemDto(
			item.getId(),
			item.getOrderItemId(),
			item.getRecordType(),
			item.getAmount(),
			item.getFeeRate(),
			TwoStagePayoutCalculator.rate(item.getAmount(), item.getFeeRate()),
			item.getTargetDate(),
			payout.getId(),
			payout.getPayoutCode(),
			payout.getRaceId(),
			payout.getRaceName(),
			payout.getStatus(),
			payout.getScheduledPayDate()
		);
	}
}
