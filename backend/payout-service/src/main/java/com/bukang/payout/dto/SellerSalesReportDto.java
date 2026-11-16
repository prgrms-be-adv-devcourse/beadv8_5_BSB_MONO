package com.bukang.payout.dto;

import java.time.YearMonth;

import com.bukang.payout.domain.PayoutTaxInvoice;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 판매자 월 매출 참고 자료 (BUK-246). 판매자가 부가세를 신고할 때 참고한다
 * 과세 · 면세 판단은 판매자 몫이라, 매출 합계와 과세일 때의 공급가액 · 세액을 함께 준다
 */
@Schema(description = "판매자 월 매출 참고 자료")
public record SellerSalesReportDto(
	@Schema(description = "매출 월 (정산 생성일 기준, yyyy-MM)", example = "2026-10")
	String month,
	@Schema(description = "매출 합계 = 판매 − 판매 취소 (참가권, 키트 포함)", example = "1100000")
	long salesAmount,
	@Schema(description = "과세 사업자일 때 공급가액 = 매출 합계 − 세액", example = "1000000")
	long taxableSupplyAmount,
	@Schema(description = "과세 사업자일 때 세액 = 매출 합계 × 10/110 (원 미만 버림)", example = "100000")
	long taxableTaxAmount
) {
	public static SellerSalesReportDto of(YearMonth month, long salesAmount) {
		long tax = PayoutTaxInvoice.taxOf(salesAmount);
		return new SellerSalesReportDto(month.toString(), salesAmount, salesAmount - tax, tax);
	}
}
