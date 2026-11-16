package com.bukang.payout.out;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Component;

import com.bukang.payout.domain.PayoutTaxInvoice;

/**
 * 홈택스 발행 목업 (BUK-246). 국세청 승인번호와 같은 모양(작성일 8자리-8자리-8자리)의 번호를 만들어 돌려준다
 * shortcut: 실제 국세청에 신고되지 않는다, 실서비스 전에 전자세금계산서 ASP 어댑터로 바꾼다
 */
@Component
public class MockHometaxAdapter implements TaxInvoiceIssuePort {
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

	@Override
	public String issue(PayoutTaxInvoice invoice, SellerBusinessPort.SellerBusiness buyer) {
		String today = LocalDate.now(ZoneId.of("Asia/Seoul")).format(DATE);
		return "%s-41000000-%08d".formatted(today, ThreadLocalRandom.current().nextInt(100_000_000));
	}
}
