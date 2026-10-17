package com.bukang.payout.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// 기대값 원본: docs-local/payout-two-stage-formula.md 3장(검산 1) · 4장(검산 2)
class TwoStagePayoutCalculatorTest {
	private static final List<Long> NONE = List.of();

	private static List<Long> tickets(long price, int count) {
		return Collections.nCopies(count, price);
	}

	// 1차 지급액 − 회수액 + 최종 지급액 = 판매자 정산액 (판매자가 받는 돈 합계)
	private static void assertSellerTotal(TwoStageAmounts amounts) {
		assertThat(amounts.firstPayoutAmount() - amounts.recoveryAmount() + amounts.finalPayoutAmount())
			.isEqualTo(amounts.sellerSettlementAmount());
	}

	@Nested
	@DisplayName("검산 1: 5만 원 × 2장")
	class FiftyThousandTimesTwo {
		@Test
		@DisplayName("환불 없음: 1차 40,000 / 수수료 5,500 / 판매자 정산액 94,500 / 최종 54,500")
		void noRefund() {
			TwoStageAmounts amounts = TwoStagePayoutCalculator.calculate(tickets(50_000L, 2), NONE, NONE);

			assertThat(amounts.firstPayoutAmount()).isEqualTo(40_000L);
			assertThat(amounts.recoveryAmount()).isZero();
			assertThat(amounts.netSalesAmount()).isEqualTo(100_000L);
			assertThat(amounts.feeAmount()).isEqualTo(5_500L);
			assertThat(amounts.sellerSettlementAmount()).isEqualTo(94_500L);
			assertThat(amounts.advanceDeductionAmount()).isEqualTo(40_000L);
			assertThat(amounts.finalPayoutAmount()).isEqualTo(54_500L);
			assertSellerTotal(amounts);
		}

		@Test
		@DisplayName("R0 마감 전 1장 환불: 판매 내역에서 빠져 1장으로 계산한다 (1차 20,000 / 최종 27,250)")
		void refundBeforeDeadline() {
			TwoStageAmounts amounts = TwoStagePayoutCalculator.calculate(tickets(50_000L, 1), NONE, NONE);

			assertThat(amounts.firstPayoutAmount()).isEqualTo(20_000L);
			assertThat(amounts.feeAmount()).isEqualTo(2_750L);
			assertThat(amounts.sellerSettlementAmount()).isEqualTo(47_250L);
			assertThat(amounts.finalPayoutAmount()).isEqualTo(27_250L);
			assertSellerTotal(amounts);
		}

		@Test
		@DisplayName("R1 1차 지급 전 1장 환불: 1차는 음수 항목을 반영한 20,000, 최종 27,250")
		void refundBeforeFirstPay() {
			TwoStageAmounts amounts = TwoStagePayoutCalculator.calculate(tickets(50_000L, 2), List.of(50_000L), NONE);

			assertThat(amounts.firstPayoutAmount()).isEqualTo(20_000L);
			assertThat(amounts.recoveryAmount()).isZero();
			assertThat(amounts.netSalesAmount()).isEqualTo(50_000L);
			assertThat(amounts.feeAmount()).isEqualTo(2_750L);
			assertThat(amounts.finalPayoutAmount()).isEqualTo(27_250L);
			assertSellerTotal(amounts);
		}

		@Test
		@DisplayName("R2 1차 지급 뒤 1장 환불: 1차 40,000 / 회수 20,000 / 최종 27,250")
		void refundAfterFirstPay() {
			TwoStageAmounts amounts = TwoStagePayoutCalculator.calculate(tickets(50_000L, 2), NONE, List.of(50_000L));

			assertThat(amounts.firstPayoutAmount()).isEqualTo(40_000L);
			assertThat(amounts.recoveryAmount()).isEqualTo(20_000L);
			assertThat(amounts.feeAmount()).isEqualTo(2_750L);
			assertThat(amounts.advanceDeductionAmount()).isEqualTo(20_000L);
			assertThat(amounts.finalPayoutAmount()).isEqualTo(27_250L);
			assertSellerTotal(amounts);
		}

		@Test
		@DisplayName("R2 1차 지급 뒤 대회 취소: 1차 40,000을 전액 회수하고 최종 0")
		void raceCanceledAfterFirstPay() {
			TwoStageAmounts amounts = TwoStagePayoutCalculator.calculate(tickets(50_000L, 2), NONE,
				tickets(50_000L, 2));

			assertThat(amounts.firstPayoutAmount()).isEqualTo(40_000L);
			assertThat(amounts.recoveryAmount()).isEqualTo(40_000L);
			assertThat(amounts.netSalesAmount()).isZero();
			assertThat(amounts.feeAmount()).isZero();
			assertThat(amounts.finalPayoutAmount()).isZero();
			assertSellerTotal(amounts);
		}
	}

	@Nested
	@DisplayName("검산 2: 33,333원 × 10장 (장마다 버림)")
	class PerTicketRounding {
		@Test
		@DisplayName("환불 없음: 1차 133,330 / 수수료 18,330 / 최종 181,670 (지름길 순매출 × 60% − 수수료라면 181,668)")
		void noRefund() {
			TwoStageAmounts amounts = TwoStagePayoutCalculator.calculate(tickets(33_333L, 10), NONE, NONE);

			assertThat(amounts.firstPayoutAmount()).isEqualTo(133_330L);
			assertThat(amounts.netSalesAmount()).isEqualTo(333_330L);
			assertThat(amounts.feeAmount()).isEqualTo(18_330L);
			assertThat(amounts.sellerSettlementAmount()).isEqualTo(315_000L);
			assertThat(amounts.finalPayoutAmount()).isEqualTo(181_670L);
			assertSellerTotal(amounts);
		}

		@Test
		@DisplayName("R1 지급 전 3장: 1차 93,331 / 수수료 12,831 / 최종 127,169")
		void refundBeforeFirstPay() {
			TwoStageAmounts amounts = TwoStagePayoutCalculator.calculate(tickets(33_333L, 10), tickets(33_333L, 3),
				NONE);

			assertThat(amounts.firstPayoutAmount()).isEqualTo(93_331L);
			assertThat(amounts.netSalesAmount()).isEqualTo(233_331L);
			assertThat(amounts.feeAmount()).isEqualTo(12_831L);
			assertThat(amounts.sellerSettlementAmount()).isEqualTo(220_500L);
			assertThat(amounts.finalPayoutAmount()).isEqualTo(127_169L);
			assertSellerTotal(amounts);
		}

		@Test
		@DisplayName("R2 지급 뒤 3장: 1차 133,330 / 회수 39,999 / 최종 127,169")
		void refundAfterFirstPay() {
			TwoStageAmounts amounts = TwoStagePayoutCalculator.calculate(tickets(33_333L, 10), NONE,
				tickets(33_333L, 3));

			assertThat(amounts.firstPayoutAmount()).isEqualTo(133_330L);
			assertThat(amounts.recoveryAmount()).isEqualTo(39_999L);
			assertThat(amounts.sellerSettlementAmount()).isEqualTo(220_500L);
			assertThat(amounts.finalPayoutAmount()).isEqualTo(127_169L);
			assertSellerTotal(amounts);
		}

		@Test
		@DisplayName("R2 대회 취소: 회수도 장마다 계산해 133,330 (합계에 곱한 133,332가 아니다), 최종 0")
		void raceCanceled() {
			TwoStageAmounts amounts = TwoStagePayoutCalculator.calculate(tickets(33_333L, 10), NONE,
				tickets(33_333L, 10));

			assertThat(amounts.recoveryAmount()).isEqualTo(133_330L);
			assertThat(amounts.sellerSettlementAmount()).isZero();
			assertThat(amounts.finalPayoutAmount()).isZero();
			assertSellerTotal(amounts);
		}
	}
}
