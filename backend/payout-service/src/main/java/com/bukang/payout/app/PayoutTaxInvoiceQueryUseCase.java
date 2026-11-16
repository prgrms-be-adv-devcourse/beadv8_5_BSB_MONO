package com.bukang.payout.app;

import java.time.YearMonth;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.payout.dto.PayoutTaxInvoiceDto;
import com.bukang.payout.dto.SellerSalesReportDto;
import com.bukang.payout.out.PayoutItemRepository;
import com.bukang.payout.out.PayoutTaxInvoiceRepository;

import lombok.RequiredArgsConstructor;

// 수수료 세금계산서 · 월 매출 참고 자료 조회 (BUK-246)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PayoutTaxInvoiceQueryUseCase {
	private final PayoutTaxInvoiceRepository payoutTaxInvoiceRepository;
	private final PayoutItemRepository payoutItemRepository;

	public List<PayoutTaxInvoiceDto> sellerInvoices(long sellerId) {
		return payoutTaxInvoiceRepository.findBySellerIdOrderBySupplyMonthDesc(sellerId).stream()
			.map(PayoutTaxInvoiceDto::from)
			.toList();
	}

	public List<PayoutTaxInvoiceDto> monthInvoices(YearMonth month) {
		return payoutTaxInvoiceRepository.findBySupplyMonthOrderBySellerId(month.toString()).stream()
			.map(PayoutTaxInvoiceDto::from)
			.toList();
	}

	public SellerSalesReportDto salesReport(long sellerId, YearMonth month) {
		long sales = payoutItemRepository.sumSales(sellerId, month.atDay(1), month.atEndOfMonth());
		return SellerSalesReportDto.of(month, sales);
	}
}
