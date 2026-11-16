package com.bukang.payout.dto;

import java.time.LocalDateTime;

import com.bukang.payout.domain.PayoutTaxInvoice;
import com.bukang.payout.domain.PayoutTaxInvoiceStatus;

import io.swagger.v3.oas.annotations.media.Schema;

// 수수료 세금계산서 한 장 (BUK-246). 공급자는 크루런이라 응답에 넣지 않는다
@Schema(description = "수수료 세금계산서")
public record PayoutTaxInvoiceDto(
	long id,
	long sellerId,
	@Schema(description = "공급 월 (지급 완료월, yyyy-MM)", example = "2026-10")
	String supplyMonth,
	@Schema(description = "수수료 합계 (부가세 포함)", example = "8250")
	long totalAmount,
	@Schema(description = "공급가액 = 합계 − 세액", example = "7500")
	long supplyAmount,
	@Schema(description = "세액 = 합계 × 10/110 (원 미만 버림)", example = "750")
	long taxAmount,
	PayoutTaxInvoiceStatus status,
	@Schema(description = "승인번호 (목업). 발행 실패면 null", example = "20261101-41000000-01234567")
	String approvalNumber,
	@Schema(description = "발행할 때 복사한 공급받는자 상호", example = "크루런 스포츠")
	String businessName,
	@Schema(description = "발행할 때 복사한 공급받는자 사업자등록번호", example = "123-45-67890")
	String businessNumber,
	@Schema(description = "발행할 때 복사한 공급받는자 대표자", example = "김러너")
	String representativeName,
	LocalDateTime issuedAt,
	@Schema(description = "마지막 실패 사유. 발행 완료면 null")
	String failReason
) {
	public static PayoutTaxInvoiceDto from(PayoutTaxInvoice invoice) {
		return new PayoutTaxInvoiceDto(invoice.getId(), invoice.getSellerId(), invoice.getSupplyMonth(),
			invoice.getTotalAmount(), invoice.getSupplyAmount(), invoice.getTaxAmount(), invoice.getStatus(),
			invoice.getApprovalNumber(), invoice.getBusinessName(), invoice.getBusinessNumber(),
			invoice.getRepresentativeName(), invoice.getIssuedAt(), invoice.getFailReason());
	}
}
