package com.bukang.payout.out;

import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;

import com.bukang.payout.domain.PayoutTaxInvoice;

/**
 * 홈택스 발행 목업 (BUK-246). 국세청 승인번호와 같은 모양(작성일 8자리-8자리-8자리)의 번호를 만들어 돌려준다
 * 멱등 계약을 지키려고 번호를 세금계산서 ID와 공급 월에서만 만든다. 같은 ID로 다시 불러도 같은 번호다
 * shortcut: 실제 국세청에 신고되지 않는다, 실서비스 전에 전자세금계산서 ASP 어댑터로 바꾼다
 */
@Component
public class MockHometaxAdapter implements TaxInvoiceIssuePort {
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

	@Override
	public String issue(PayoutTaxInvoice invoice, SellerBusinessPort.SellerBusiness buyer) {
		// 작성일은 공급 월 다음 달 1일(배치가 처음 발행하는 날)로 고정한다
		String writtenAt = invoice.getSupplyYearMonth().plusMonths(1).atDay(1).format(DATE);
		return "%s-41000000-%08d".formatted(writtenAt, invoice.getId());
	}
}
