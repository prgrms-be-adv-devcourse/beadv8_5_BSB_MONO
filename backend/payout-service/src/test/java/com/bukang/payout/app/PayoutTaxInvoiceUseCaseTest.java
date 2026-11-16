package com.bukang.payout.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.common.shared.seller.domain.SellerType;
import com.bukang.payout.domain.BusinessDayCalendar;
import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutCreateFailure;
import com.bukang.payout.domain.PayoutCreateFailureType;
import com.bukang.payout.domain.PayoutTaxInvoice;
import com.bukang.payout.domain.PayoutTaxInvoiceStatus;
import com.bukang.payout.out.PayoutCreateFailureRepository;
import com.bukang.payout.out.PayoutItemRepository;
import com.bukang.payout.out.PayoutRepository;
import com.bukang.payout.out.PayoutTaxInvoiceRepository;
import com.bukang.payout.out.SellerBusinessPort;
import com.bukang.payout.out.SellerBusinessPort.SellerBusiness;

/**
 * 사업자 판매자에게 지난달 수수료 세금계산서를 한 장씩 발행한다 (BUK-246)
 */
@DataJpaTest
@ActiveProfiles("test")
class PayoutTaxInvoiceUseCaseTest {
	private static final long SELLER_ID = 1;
	private static final BigDecimal FEE_RATE = new BigDecimal("0.0550");
	// 11/1 05:00 배치: 10월 지급 완료분이 대상이다
	private static final LocalDate TODAY = LocalDate.of(2026, 11, 1);
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 1, 5, 0);
	private static final SellerBusiness BUSINESS = new SellerBusiness(SellerType.BUSINESS, "크루런 스포츠",
		"123-45-67890", "김러너");
	private static final SellerBusiness INDIVIDUAL = new SellerBusiness(SellerType.INDIVIDUAL, null, null, "김러너");

	@Autowired
	private PayoutRepository payoutRepository;
	@Autowired
	private PayoutItemRepository payoutItemRepository;
	@Autowired
	private PayoutTaxInvoiceRepository payoutTaxInvoiceRepository;
	@Autowired
	private PayoutCreateFailureRepository payoutCreateFailureRepository;
	@Autowired
	private PlatformTransactionManager transactionManager;

	private long nextRaceId = 10;
	private long nextOrderItemId = 1;

	// 이상 감지 기록은 따로 커밋돼(REQUIRES_NEW) 테스트 롤백으로 지워지지 않는다
	@AfterEach
	void deleteFailures() {
		TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
		requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		requiresNew.executeWithoutResult(status -> payoutCreateFailureRepository.deleteAllInBatch());
	}

	private PayoutTaxInvoiceUseCase useCase(SellerBusinessPort sellerBusinessPort) {
		return new PayoutTaxInvoiceUseCase(payoutRepository, payoutTaxInvoiceRepository, payoutCreateFailureRepository,
			sellerBusinessPort, (invoice, buyer) -> "20261101-41000000-00000001");
	}

	// 참가권 한 장(price)짜리 최종 정산 내역을 paidAt에 지급 완료로 만든다. 수수료 = price × 5.5%(원 미만 버림)
	private Payout savePaid(long sellerId, long price, LocalDateTime paidAt) {
		Payout payout = savePayout(sellerId, price);
		payout.pay(paidAt);
		return payout;
	}

	private Payout savePayout(long sellerId, long price) {
		LocalDate raceDate = LocalDate.of(2026, 9, 20);
		Payout payout = Payout.finalStage(sellerId, nextRaceId++, "테스트 대회", raceDate, raceDate.atTime(3, 0),
			new BusinessDayCalendar());
		payout.addItem(nextOrderItemId++, price, FEE_RATE, raceDate.plusDays(1));
		return payoutRepository.save(payout);
	}

	@Test
	@DisplayName("세액은 합계 × 10/110에서 원 미만을 버리고, 공급가액은 합계 − 세액이다")
	void taxIsTotalTimesTenOverHundredTen() {
		assertThat(PayoutTaxInvoice.taxOf(8_250)).isEqualTo(750);
		// 1,000 × 10/110 = 90.9… → 90
		PayoutTaxInvoice invoice = new PayoutTaxInvoice(SELLER_ID, YearMonth.of(2026, 10), 1_000);
		assertThat(invoice.getTaxAmount()).isEqualTo(90);
		assertThat(invoice.getSupplyAmount()).isEqualTo(910);
	}

	@Test
	@DisplayName("지난달 지급 완료된 수수료를 판매자마다 더해 한 장 발행하고, 공급받는자 정보를 복사해 둔다")
	void issuesOneInvoicePerSellerForLastMonth() {
		// 수수료 2,750 + 5,500 = 8,250
		savePaid(SELLER_ID, 50_000, LocalDateTime.of(2026, 10, 20, 4, 0));
		savePaid(SELLER_ID, 100_000, LocalDateTime.of(2026, 10, 31, 23, 0));

		PayoutTaxInvoiceUseCase.Result result = useCase(sellerId -> BUSINESS).run(TODAY, NOW);

		assertThat(result.issued()).isEqualTo(1);
		PayoutTaxInvoice invoice = payoutTaxInvoiceRepository.findBySellerIdAndSupplyMonth(SELLER_ID, "2026-10")
			.orElseThrow();
		assertThat(invoice.getStatus()).isEqualTo(PayoutTaxInvoiceStatus.발행_완료);
		assertThat(invoice.getTotalAmount()).isEqualTo(8_250);
		assertThat(invoice.getSupplyAmount()).isEqualTo(7_500);
		assertThat(invoice.getTaxAmount()).isEqualTo(750);
		assertThat(invoice.getBusinessNumber()).isEqualTo("123-45-67890");
		assertThat(invoice.getApprovalNumber()).isEqualTo("20261101-41000000-00000001");
	}

	@Test
	@DisplayName("이번 달에 지급됐거나 아직 지급 전인 정산 내역의 수수료는 넣지 않는다")
	void excludesOtherMonthsAndUnpaid() {
		savePaid(SELLER_ID, 50_000, LocalDateTime.of(2026, 10, 20, 4, 0));
		savePaid(SELLER_ID, 100_000, LocalDateTime.of(2026, 11, 1, 4, 0));
		savePayout(SELLER_ID, 100_000);

		useCase(sellerId -> BUSINESS).run(TODAY, NOW);

		assertThat(payoutTaxInvoiceRepository.findAll()).singleElement()
			.extracting(PayoutTaxInvoice::getTotalAmount).isEqualTo(2_750L);
	}

	@Test
	@DisplayName("비사업자 판매자와 수수료가 0원인 판매자에게는 발행하지 않는다")
	void skipsIndividualAndZeroFee() {
		savePaid(SELLER_ID, 50_000, LocalDateTime.of(2026, 10, 20, 4, 0));
		// 10원 × 5.5% = 0.55 → 수수료 0원
		savePaid(2, 10, LocalDateTime.of(2026, 10, 20, 4, 0));

		PayoutTaxInvoiceUseCase.Result result = useCase(sellerId -> INDIVIDUAL).run(TODAY, NOW);

		assertThat(result.notBusiness()).isEqualTo(1);
		assertThat(payoutTaxInvoiceRepository.findAll()).isEmpty();
	}

	@Test
	@DisplayName("판매자 정보를 받지 못하면 발행 실패로 남기고 이상 감지에 기록하며, 다음 실행에서 같은 행을 발행한다")
	void failsThenRetriesNextDay() {
		savePaid(SELLER_ID, 50_000, LocalDateTime.of(2026, 10, 20, 4, 0));

		PayoutTaxInvoiceUseCase.Result failed = useCase(sellerId -> {
			throw new IllegalStateException("회원 서비스 응답 없음");
		}).run(TODAY, NOW);

		assertThat(failed.failed()).isEqualTo(1);
		assertThat(payoutTaxInvoiceRepository.findAll()).singleElement()
			.extracting(PayoutTaxInvoice::getStatus).isEqualTo(PayoutTaxInvoiceStatus.발행_실패);
		assertThat(payoutCreateFailureRepository.findAll()).singleElement()
			.extracting(PayoutCreateFailure::getFailureType).isEqualTo(PayoutCreateFailureType.세금계산서_발행_실패);

		useCase(sellerId -> BUSINESS).run(TODAY.plusDays(1), NOW.plusDays(1));

		assertThat(payoutTaxInvoiceRepository.findAll()).singleElement()
			.extracting(PayoutTaxInvoice::getStatus).isEqualTo(PayoutTaxInvoiceStatus.발행_완료);
	}

	@Test
	@DisplayName("이미 발행한 달은 다시 돌려도 판매자 정보를 묻지 않고 한 장 그대로다")
	void doesNotReissue() {
		savePaid(SELLER_ID, 50_000, LocalDateTime.of(2026, 10, 20, 4, 0));
		AtomicInteger lookups = new AtomicInteger();
		SellerBusinessPort countingPort = sellerId -> {
			lookups.incrementAndGet();
			return BUSINESS;
		};

		useCase(countingPort).run(TODAY, NOW);
		useCase(countingPort).run(TODAY.plusDays(1), NOW.plusDays(1));

		assertThat(lookups).hasValue(1);
		assertThat(payoutTaxInvoiceRepository.findAll()).hasSize(1);
	}

	@Test
	@DisplayName("다음 달 10일이 지나도 발행하지 못하면 이상 감지 종류가 기한 지남이고, 다음 달이 돼도 다시 시도한다")
	void overdueAfterTenthAndStillRetried() {
		savePaid(SELLER_ID, 50_000, LocalDateTime.of(2026, 10, 20, 4, 0));
		SellerBusinessPort failing = sellerId -> {
			throw new IllegalStateException("회원 서비스 응답 없음");
		};
		useCase(failing).run(TODAY, NOW);

		// 12/1: 지난달은 11월이지만 10월 발행 실패도 다시 시도한다
		useCase(failing).run(LocalDate.of(2026, 12, 1), NOW.plusMonths(1));

		assertThat(payoutCreateFailureRepository.findAll())
			.extracting(PayoutCreateFailure::getFailureType)
			.containsExactly(PayoutCreateFailureType.세금계산서_발행_실패, PayoutCreateFailureType.세금계산서_기한_지남);
	}

	@Test
	@DisplayName("월 매출 참고 자료는 그달 판매에서 판매 취소를 빼고, 과세일 때 공급가액 · 세액을 함께 준다")
	void salesReportNetsCanceledSales() {
		savePayout(SELLER_ID, 110_000);
		Payout canceled = savePayout(SELLER_ID, 55_000);
		canceled.cancel(99, NOW, "환불");
		payoutRepository.flush();

		var report = new PayoutTaxInvoiceQueryUseCase(payoutTaxInvoiceRepository, payoutItemRepository)
			.salesReport(SELLER_ID, YearMonth.of(2026, 9));

		assertThat(report.salesAmount()).isEqualTo(110_000);
		assertThat(report.taxableTaxAmount()).isEqualTo(10_000);
		assertThat(report.taxableSupplyAmount()).isEqualTo(100_000);
	}
}
