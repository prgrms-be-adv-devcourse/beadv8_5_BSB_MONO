package com.bukang.payout.dto;

import java.time.LocalDate;
import java.util.List;

import com.bukang.payout.domain.PayoutTaxQuarter;
import com.bukang.payout.out.PayoutItemRepository.SellerSales;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 통신판매중개자 분기 판매 자료 (BUK-246, 부가가치세법 75조 · 시행규칙 별지 제48호서식)
 * 판매자 상호 · 사업자번호는 관리자 판매자 목록과 합쳐서 보여 준다 (정산은 판매자 정보를 갖지 않는다)
 */
@Schema(description = "통신판매중개자 분기 판매 자료")
public record PayoutQuarterSalesReportDto(
	@Schema(description = "연도", example = "2026")
	int year,
	@Schema(description = "분기 (1 ~ 4)", example = "4")
	int quarter,
	@Schema(description = "집계 시작일 (정산 생성일 기준)", example = "2026-10-01")
	LocalDate from,
	@Schema(description = "집계 끝일 (포함)", example = "2026-12-31")
	LocalDate to,
	@Schema(description = "제출 기한: 분기 말일의 다음 달 15일", example = "2027-01-15")
	LocalDate dueDate,
	@Schema(description = "판매 건수 합계 (판매 − 판매 취소)", example = "182")
	long totalCount,
	@Schema(description = "판매 금액 합계 (판매 − 판매 취소)", example = "23350000")
	long totalAmount,
	List<SellerSalesDto> sellers
) {
	public static PayoutQuarterSalesReportDto of(PayoutTaxQuarter quarter, List<SellerSales> sales) {
		List<SellerSalesDto> sellers = sales.stream()
			.map(sale -> new SellerSalesDto(sale.sellerId(), sale.salesCount(), sale.salesAmount()))
			.toList();
		return new PayoutQuarterSalesReportDto(quarter.year(), quarter.quarter(), quarter.from(), quarter.to(),
			quarter.dueDate(), sellers.stream().mapToLong(SellerSalesDto::salesCount).sum(),
			sellers.stream().mapToLong(SellerSalesDto::salesAmount).sum(), sellers);
	}

	@Schema(description = "판매자 한 명의 분기 판매")
	public record SellerSalesDto(
		@Schema(description = "판매자 ID", example = "3")
		long sellerId,
		@Schema(description = "판매 건수 (참가권 장수)", example = "120")
		long salesCount,
		@Schema(description = "판매 금액", example = "13560000")
		long salesAmount
	) {
	}
}
