package com.bukang.payout.out;

import com.bukang.payout.domain.PayoutTaxInvoice;

/**
 * 세금계산서를 국세청(홈택스)에 발행하고 승인번호를 받는 계약 (BUK-246)
 * 지금은 목업 어댑터(MockHometaxAdapter)만 있다. 실제 연동(전자세금계산서 ASP)을 붙이면 어댑터만 바꾼다
 */
@FunctionalInterface
public interface TaxInvoiceIssuePort {
	/**
	 * 멱등 계약: 같은 세금계산서 ID(invoice.getId())로 여러 번 불러도 국세청에는 한 장만 발행하고 같은 승인번호를 돌려준다.
	 * 배치는 발행 요청 전에 행을 저장해 ID를 정하고, 발행 뒤 저장이 실패하면 다음 날 같은 ID로 다시 부른다 (PR 531 리뷰)
	 *
	 * @return 승인번호
	 * @throws RuntimeException 발행 실패. 배치는 발행 실패로 남기고 다음 날 다시 시도한다
	 */
	String issue(PayoutTaxInvoice invoice, SellerBusinessPort.SellerBusiness buyer);
}
