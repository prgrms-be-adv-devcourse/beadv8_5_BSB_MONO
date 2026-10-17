package com.bukang.payout.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 1차 · 최종 2단계 정산 금액 (docs-local/payout-two-stage-formula.md 1장, payout.md 4-1)
 * 모든 비율은 참가권 1장마다 계산해 원 미만을 버린 뒤 더한다 (1차 몫 · 수수료 · 회수 모두, Q7)
 * 버림은 DOWN(0 쪽으로)이다. 음수 항목에서 FLOOR는 1원 더 빼므로 쓰지 않는다 (PRO-114)
 */
public final class TwoStagePayoutCalculator {
	// 1차 정산금 비율. 수수료는 떼지 않는다
	public static final BigDecimal FIRST_RATE = new BigDecimal("0.40");
	// 플랫폼 수수료율 5.5% (부가세 포함). 최종 정산에서만 뗀다
	public static final BigDecimal FEE_RATE = new BigDecimal("0.0550");

	private TwoStagePayoutCalculator() {
	}

	// 참가권 1장의 1차 몫. 회수액도 이 값으로 센다 (1차 때 준 몫을 그대로 돌려받는다)
	public static long firstShare(long price) {
		return rate(price, FIRST_RATE);
	}

	// 참가권 1장의 금액 × 비율, 원 미만 버림
	public static long rate(long amount, BigDecimal rate) {
		return BigDecimal.valueOf(amount).multiply(rate).setScale(0, RoundingMode.DOWN).longValueExact();
	}

	/**
	 * 2단계 정산 금액을 한 번에 계산한다. 모두 참가권 1장 가격의 목록이다
	 *
	 * @param soldPrices            1차 정산을 만들 때 살아 있던 참가권 (환불 마감일까지 환불된 R0은 넣지 않는다)
	 * @param refundedBeforeFirstPay soldPrices 중 1차 지급 전에 환불된 참가권 (R1, 1차의 음수 항목)
	 * @param refundedAfterFirstPay  soldPrices 중 1차 지급 뒤에 환불된 참가권 (R2, 회수)
	 */
	public static TwoStageAmounts calculate(List<Long> soldPrices, List<Long> refundedBeforeFirstPay,
		List<Long> refundedAfterFirstPay) {
		long firstPayout = sumFirstShare(soldPrices) - sumFirstShare(refundedBeforeFirstPay);
		long recovery = sumFirstShare(refundedAfterFirstPay);

		long sales = sum(soldPrices);
		long refund = sum(refundedBeforeFirstPay) + sum(refundedAfterFirstPay);
		// 남은 참가권의 수수료 합. 장마다 계산하므로 '전체 − 환불분'이 '남은 장의 합'과 같다
		long fee = sumFee(soldPrices) - sumFee(refundedBeforeFirstPay) - sumFee(refundedAfterFirstPay);

		return TwoStageAmounts.of(firstPayout, recovery, sales, refund, fee);
	}

	private static long sum(List<Long> prices) {
		return prices.stream().mapToLong(Long::longValue).sum();
	}

	private static long sumFirstShare(List<Long> prices) {
		return prices.stream().mapToLong(TwoStagePayoutCalculator::firstShare).sum();
	}

	private static long sumFee(List<Long> prices) {
		return prices.stream().mapToLong(price -> rate(price, FEE_RATE)).sum();
	}
}
