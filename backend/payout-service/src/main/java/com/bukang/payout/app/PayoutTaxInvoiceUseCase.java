package com.bukang.payout.app;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import org.springframework.stereotype.Service;

import com.bukang.payout.domain.PayoutCreateFailure;
import com.bukang.payout.domain.PayoutCreateFailureType;
import com.bukang.payout.domain.PayoutTaxInvoice;
import com.bukang.payout.domain.PayoutTaxInvoiceStatus;
import com.bukang.payout.out.PayoutCreateFailureRepository;
import com.bukang.payout.out.PayoutRepository;
import com.bukang.payout.out.PayoutRepository.SellerFee;
import com.bukang.payout.out.PayoutTaxInvoiceRepository;
import com.bukang.payout.out.SellerBusinessPort;
import com.bukang.payout.out.SellerBusinessPort.SellerBusiness;
import com.bukang.payout.out.TaxInvoiceIssuePort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 사업자 판매자에게 지난달 수수료 세금계산서를 발행한다 (BUK-246). 매일 05:00 배치가 부른다
 * - 대상: 지난달 지급 완료된 정산 내역의 수수료 합계가 0원보다 큰 판매자. 판매자 × 공급 월마다 한 장
 * - 비사업자는 세금계산서를 받지 않으므로 행을 만들지 않는다
 * - 판매자 정보 조회나 발행이 실패하면 발행 실패로 남기고 이상 감지에 기록한다. 지난 달들의 발행 실패도 매일 다시 시도한다
 * - 공급 월 다음 달 10일(법정 기한)이 지나도 실패하면 이상 감지 종류를 '기한 지남'으로 남긴다
 * 회원 서비스 · 발행 요청을 부르는 동안 DB 연결을 잡지 않도록 트랜잭션을 열지 않고, 저장은 한 장씩 따로 커밋한다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutTaxInvoiceUseCase {
	// 공급 월 다음 달의 이날까지 발행해야 한다 (부가가치세법 34조 월합계 세금계산서)
	static final int DUE_DAY_OF_NEXT_MONTH = 10;

	private final PayoutRepository payoutRepository;
	private final PayoutTaxInvoiceRepository payoutTaxInvoiceRepository;
	private final PayoutCreateFailureRepository payoutCreateFailureRepository;
	private final SellerBusinessPort sellerBusinessPort;
	private final TaxInvoiceIssuePort taxInvoiceIssuePort;

	public Result run(LocalDate today, LocalDateTime now) {
		YearMonth lastMonth = YearMonth.from(today).minusMonths(1);
		Result result = new Result();
		// shortcut: 비사업자는 행이 없어 그달 내내 매일 다시 조회한다, 판매자가 많아지면 제외 상태 행을 둔다
		for (SellerFee fee : payoutRepository.sumPaidFeeBySeller(lastMonth.atDay(1).atStartOfDay(),
			lastMonth.plusMonths(1).atDay(1).atStartOfDay())) {
			PayoutTaxInvoice invoice = payoutTaxInvoiceRepository
				.findBySellerIdAndSupplyMonth(fee.sellerId(), lastMonth.toString())
				.orElseGet(() -> new PayoutTaxInvoice(fee.sellerId(), lastMonth, fee.feeAmount()));
			if (!invoice.isIssued()) {
				issue(invoice, today, now, result);
			}
		}
		List<PayoutTaxInvoice> olderFailures = payoutTaxInvoiceRepository
			.findByStatusAndSupplyMonthLessThan(PayoutTaxInvoiceStatus.발행_실패, lastMonth.toString());
		olderFailures.forEach(invoice -> issue(invoice, today, now, result));
		log.info("수수료 세금계산서 배치를 마쳤습니다. today={}, issued={}, failed={}, notBusiness={}", today,
			result.issued, result.failed, result.notBusiness);
		return result;
	}

	private void issue(PayoutTaxInvoice invoice, LocalDate today, LocalDateTime now, Result result) {
		try {
			SellerBusiness buyer = sellerBusinessPort.find(invoice.getSellerId());
			if (!buyer.isBusiness()) {
				// 첫 조회가 실패해 발행 실패 행이 남았던 비사업자면 지운다. 남겨 두면 매일 다시 조회되고 목록에 실패로 보인다 (PR 531 리뷰)
				if (invoice.getId() != 0) {
					payoutTaxInvoiceRepository.delete(invoice);
				}
				result.notBusiness++;
				return;
			}
			// 발행 요청 전에 행을 먼저 저장해 ID를 정한다. 발행 뒤 저장이 실패해 다음 날 다시 불러도
			// 같은 ID로 부르므로 발행 쪽이 한 장으로 처리한다 (TaxInvoiceIssuePort의 멱등 계약, PR 531 리뷰)
			payoutTaxInvoiceRepository.save(invoice);
			String approvalNumber = taxInvoiceIssuePort.issue(invoice, buyer);
			invoice.issue(buyer.businessName(), buyer.businessNumber(), buyer.representativeName(), approvalNumber,
				now);
			payoutTaxInvoiceRepository.save(invoice);
			result.issued++;
		} catch (RuntimeException exception) {
			log.error("수수료 세금계산서를 발행하지 못했습니다. sellerId={}, month={}", invoice.getSellerId(),
				invoice.getSupplyMonth(), exception);
			invoice.fail(exception.getClass().getSimpleName() + ": " + exception.getMessage());
			payoutTaxInvoiceRepository.save(invoice);
			PayoutCreateFailureType type = isOverdue(invoice, today)
				? PayoutCreateFailureType.세금계산서_기한_지남 : PayoutCreateFailureType.세금계산서_발행_실패;
			payoutCreateFailureRepository.save(PayoutCreateFailure.taxInvoice(type, invoice, today));
			result.failed++;
		}
	}

	private static boolean isOverdue(PayoutTaxInvoice invoice, LocalDate today) {
		return today.isAfter(invoice.getSupplyYearMonth().plusMonths(1).atDay(DUE_DAY_OF_NEXT_MONTH));
	}

	public static final class Result {
		private int issued;
		private int failed;
		private int notBusiness;

		public int issued() {
			return issued;
		}

		public int failed() {
			return failed;
		}

		public int notBusiness() {
			return notBusiness;
		}
	}
}
