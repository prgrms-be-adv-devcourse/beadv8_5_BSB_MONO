package com.bukang.payout.out;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bukang.payout.domain.PayoutTaxInvoice;
import com.bukang.payout.domain.PayoutTaxInvoiceStatus;

public interface PayoutTaxInvoiceRepository extends JpaRepository<PayoutTaxInvoice, Long> {
	Optional<PayoutTaxInvoice> findBySellerIdAndSupplyMonth(long sellerId, String supplyMonth);

	// 지난 달들에 발행하지 못한 세금계산서. 배치가 매일 다시 발행한다
	List<PayoutTaxInvoice> findByStatusAndSupplyMonthLessThan(PayoutTaxInvoiceStatus status, String supplyMonth);

	List<PayoutTaxInvoice> findBySellerIdOrderBySupplyMonthDesc(long sellerId);

	List<PayoutTaxInvoice> findBySupplyMonthOrderBySellerId(String supplyMonth);
}
