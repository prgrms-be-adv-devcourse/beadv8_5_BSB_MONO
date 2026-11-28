package com.bukang.payout.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 수수료 세금계산서 금액. 기대값은 docs/adr/0001-payout-fee-tax-invoice.md 결정 3에서 손으로 계산했다
 * - 수수료는 부가세 포함 금액이다. 세액 = 합계 × 10/110 (원 미만 버림), 공급가액 = 합계 − 세액
 */
class PayoutTaxInvoiceTest {

	@ParameterizedTest(name = "합계 {0}원 → 세액 {1}원, 공급가액 {2}원")
	@DisplayName("세액은 합계의 10/110을 원 미만 버림하고, 공급가액은 합계에서 세액을 뺀다")
	@CsvSource({
		"0, 0, 0",
		// 10/110이 1원이 안 되는 금액은 세액 0원
		"1, 0, 1",
		"10, 0, 10",
		// 세액이 처음 1원이 되는 금액
		"11, 1, 10",
		// 109 × 10/110 = 9.909… → 9원
		"109, 9, 100",
		"110, 10, 100",
		// ADR 0001 남은 일의 예: 세액을 먼저 버림하므로 공급가액 × 10%(91원)와 1원 어긋난다
		"1000, 90, 910",
		// 정가 10만 원 참가권 한 장의 수수료 5.5%
		"5500, 500, 5000",
		// 큰 금액: 11억 원, 그리고 1원 모자란 금액(99,999,999.909… → 버림)
		"1100000000, 100000000, 1000000000",
		"1099999999, 99999999, 1000000000"
	})
	void taxAndSupply(long total, long expectedTax, long expectedSupply) {
		assertThat(PayoutTaxInvoice.taxOf(total)).isEqualTo(expectedTax);
		assertThat(PayoutTaxInvoice.supplyOf(total)).isEqualTo(expectedSupply);
	}

	@Test
	@DisplayName("세금계산서를 만들면 합계 · 세액 · 공급가액이 ADR 규칙대로 채워지고, 세액 + 공급가액 = 합계다")
	void constructorFillsAmounts() {
		PayoutTaxInvoice invoice = new PayoutTaxInvoice(7, YearMonth.of(2026, 11), 1_000);

		assertThat(invoice.getTotalAmount()).isEqualTo(1_000);
		assertThat(invoice.getTaxAmount()).isEqualTo(90);
		assertThat(invoice.getSupplyAmount()).isEqualTo(910);
		assertThat(invoice.getTaxAmount() + invoice.getSupplyAmount()).isEqualTo(invoice.getTotalAmount());
		assertThat(invoice.getSupplyYearMonth()).isEqualTo(YearMonth.of(2026, 11));
	}
}
