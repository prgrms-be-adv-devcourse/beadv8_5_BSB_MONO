package com.bukang.payout.out;

import com.bukang.payout.domain.PayoutTaxInvoice;

/**
 * 세금계산서를 국세청(홈택스)에 발행하고 승인번호를 받는 계약 (BUK-246)
 * 지금은 목업 어댑터(MockHometaxAdapter)만 있다. 실제 연동(전자세금계산서 ASP)을 붙이면 어댑터만 바꾼다
 */
@FunctionalInterface
public interface TaxInvoiceIssuePort {
	/**
	 * @return 승인번호
	 * @throws RuntimeException 발행 실패. 배치는 발행 실패로 남기고 다음 날 다시 시도한다
	 */
	String issue(PayoutTaxInvoice invoice, SellerBusinessPort.SellerBusiness buyer);
}
