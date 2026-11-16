package com.bukang.payout.domain;

import static jakarta.persistence.EnumType.STRING;
import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDateTime;
import java.time.YearMonth;

import com.bukang.common.global.jpa.entity.BaseIdAndTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사업자 판매자에게 발행하는 플랫폼 수수료 세금계산서 한 장 (BUK-246)
 * 판매자 × 공급 월(지급 완료월)마다 한 장이다. 그달 지급 완료된 정산 내역의 수수료를 더한 금액이다
 * 수수료는 부가세 포함으로 보고 거꾸로 나눈다: 세액 = 합계 × 10/110(원 미만 버림), 공급가액 = 합계 − 세액
 * 공급받는자 칸(상호 · 사업자번호 · 대표자)은 발행할 때 회원 서비스에서 받아 복사해 둔다. 나중에 판매자가 정보를 바꿔도
 * 이미 발행한 세금계산서는 그대로다. 발행 뒤 금액을 고치는 수정세금계산서는 아직 없다
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(name = "PAYOUT_TAX_INVOICE",
	uniqueConstraints = @UniqueConstraint(name = "uk_payout_tax_invoice_seller_month",
		columnNames = {"seller_id", "supply_month"}))
public class PayoutTaxInvoice extends BaseIdAndTime {
	private static final int FAIL_REASON_MAX_LENGTH = 255;

	@Column(nullable = false)
	private long sellerId;

	// 공급 월 yyyy-MM (정산 내역의 정산 월 칸과 같은 모양)
	@Column(nullable = false, length = 7)
	private String supplyMonth;

	// 수수료 합계(부가세 포함)
	@Column(nullable = false)
	private long totalAmount;

	@Column(nullable = false)
	private long supplyAmount;

	@Column(nullable = false)
	private long taxAmount;

	@Enumerated(STRING)
	@Column(nullable = false, length = 10)
	private PayoutTaxInvoiceStatus status;

	// 국세청 승인번호 자리. 지금은 목업 번호다. 발행 실패면 NULL
	@Column(length = 30)
	private String approvalNumber;

	// 발행할 때 복사한 공급받는자 정보. 발행 실패면 NULL
	@Column(length = 100)
	private String businessName;

	@Column(length = 20)
	private String businessNumber;

	@Column(length = 50)
	private String representativeName;

	private LocalDateTime issuedAt;

	// 마지막 실패 사유. 발행에 성공하면 비운다
	@Column(length = FAIL_REASON_MAX_LENGTH)
	private String failReason;

	public PayoutTaxInvoice(long sellerId, YearMonth supplyMonth, long totalAmount) {
		this.sellerId = sellerId;
		this.supplyMonth = supplyMonth.toString();
		this.totalAmount = totalAmount;
		this.taxAmount = taxOf(totalAmount);
		this.supplyAmount = totalAmount - taxAmount;
		this.status = PayoutTaxInvoiceStatus.발행_실패;
	}

	// 부가세 포함 금액에서 세액을 거꾸로 구한다. 원 미만은 버린다
	public static long taxOf(long totalAmount) {
		return totalAmount * 10 / 110;
	}

	public YearMonth getSupplyYearMonth() {
		return YearMonth.parse(supplyMonth);
	}

	public boolean isIssued() {
		return status == PayoutTaxInvoiceStatus.발행_완료;
	}

	public void issue(String businessName, String businessNumber, String representativeName, String approvalNumber,
		LocalDateTime issuedAt) {
		this.businessName = businessName;
		this.businessNumber = businessNumber;
		this.representativeName = representativeName;
		this.approvalNumber = approvalNumber;
		this.issuedAt = issuedAt;
		this.status = PayoutTaxInvoiceStatus.발행_완료;
		this.failReason = null;
	}

	public void fail(String reason) {
		this.status = PayoutTaxInvoiceStatus.발행_실패;
		this.failReason = reason == null || reason.length() <= FAIL_REASON_MAX_LENGTH
			? reason : reason.substring(0, FAIL_REASON_MAX_LENGTH);
	}
}
