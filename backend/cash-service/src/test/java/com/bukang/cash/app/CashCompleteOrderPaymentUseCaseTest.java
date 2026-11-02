package com.bukang.cash.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.cash.config.CashWalletProperties;
import com.bukang.cash.domain.exception.PaymentAlreadyFinalizedException;
import com.bukang.cash.domain.member.CashMember;
import com.bukang.cash.domain.payment.Payment;
import com.bukang.cash.domain.payment.PaymentStatus;
import com.bukang.cash.domain.wallet.LedgerRefType;
import com.bukang.cash.domain.wallet.LedgerType;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.domain.wallet.WalletLedger;
import com.bukang.cash.dto.CashPaymentResponseDto;
import com.bukang.cash.out.CashMemberRepository;
import com.bukang.cash.out.PaymentRepository;
import com.bukang.cash.out.WalletLedgerRepository;
import com.bukang.cash.out.WalletRepository;

// 운영 코드는 고치지 않고, 지금 CashCompleteOrderPaymentUseCase의 동작을 기록해 두기 위한 테스트
// (docs/troubleshooting/cash-payment-before-fix.md 비교용). 실패해도 운영 코드는 손대지 않는다
@SpringBootTest
@ActiveProfiles("test")
class CashCompleteOrderPaymentUseCaseTest {
	private static final long CUSTOMER_ID = 3;

	@Autowired
	private CashFacade cashFacade;
	@Autowired
	private CashWalletProperties cashWalletProperties;
	@Autowired
	private CashMemberRepository cashMemberRepository;
	@Autowired
	private WalletRepository walletRepository;
	@Autowired
	private PaymentRepository paymentRepository;
	@Autowired
	private TransactionTemplate transactionTemplate;
	@MockitoSpyBean
	private WalletLedgerRepository walletLedgerRepository;

	@AfterEach
	void tearDown() {
		reset(walletLedgerRepository);
		paymentRepository.deleteAll();
		walletLedgerRepository.deleteAll();
		walletRepository.deleteAll();
		cashMemberRepository.deleteAll();
	}

	@Test
	@DisplayName("P1: 잔액이 충분하면 PAID, 고객 잔액 감소, 보관 잔액 증가, 장부 두 줄 합은 0, 결제는 COMPLETED")
	void p1_sufficientBalance_debitsCustomerAndCreditsHolding() {
		long orderId = 100;
		long amount = 3_000;
		createCustomerWallet(10_000);
		createHoldingWallet();

		CashPaymentResponseDto result = cashFacade.completeOrderPayment(orderId, CUSTOMER_ID, amount);

		assertThat(result.outcome()).isEqualTo(CashPaymentResponseDto.Outcome.PAID);
		assertThat(result.shortage()).isZero();
		assertBalance(CUSTOMER_ID, 7_000);
		assertBalance(cashWalletProperties.holdingMemberId(), 3_000);
		assertThat(ledgerAmountSumForOrder(orderId)).isZero();
		Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
		assertLedgerConsistency(CUSTOMER_ID, cashWalletProperties.holdingMemberId());
		assertTotalPreserved(10_000);
	}

	@Test
	@DisplayName("P2: 잔액이 부족하면 INSUFFICIENT_BALANCE와 정확한 부족 금액, 잔액·장부는 그대로, 결제는 FAILED로 저장")
	void p2_insufficientBalance_noChangeAndSavedAsFailed() {
		long orderId = 200;
		long amount = 15_000;
		createCustomerWallet(10_000);
		createHoldingWallet();

		CashPaymentResponseDto result = cashFacade.completeOrderPayment(orderId, CUSTOMER_ID, amount);

		assertThat(result.outcome()).isEqualTo(CashPaymentResponseDto.Outcome.INSUFFICIENT_BALANCE);
		assertThat(result.shortage()).isEqualTo(5_000);
		assertBalance(CUSTOMER_ID, 10_000);
		assertBalance(cashWalletProperties.holdingMemberId(), 0);
		assertThat(countLedgersForOrder(orderId)).isZero();
		Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
		assertLedgerConsistency(CUSTOMER_ID, cashWalletProperties.holdingMemberId());
		assertTotalPreserved(10_000);
	}

	@Test
	@DisplayName("P3: 잔액 부족 뒤 충전하고 같은 주문으로 재결제하면 같은 결제 행이 FAILED에서 COMPLETED로 바뀐다 (행은 하나)")
	void p3_retryAfterTopUp_reusesSamePaymentRowAndCompletes() {
		long orderId = 300;
		long amount = 15_000;
		createCustomerWallet(10_000);
		createHoldingWallet();

		CashPaymentResponseDto first = cashFacade.completeOrderPayment(orderId, CUSTOMER_ID, amount);
		assertThat(first.outcome()).isEqualTo(CashPaymentResponseDto.Outcome.INSUFFICIENT_BALANCE);
		long paymentIdAfterFailure = paymentRepository.findByOrderId(orderId).orElseThrow().getId();

		// 충전 흐름(WalletCharge)은 범위 밖이라, 충전이 끝난 상태를 지갑에 직접 적립해 흉내낸다
		transactionTemplate.executeWithoutResult(status -> {
			Wallet wallet = walletRepository.findById(CUSTOMER_ID).orElseThrow();
			walletLedgerRepository.save(
				wallet.credit(10_000, LedgerType.충전__PG결제_토스페이먼츠, LedgerRefType.WalletCharge, 2));
		});

		CashPaymentResponseDto second = cashFacade.completeOrderPayment(orderId, CUSTOMER_ID, amount);

		assertThat(second.outcome()).isEqualTo(CashPaymentResponseDto.Outcome.PAID);
		assertThat(paymentRepository.count()).isEqualTo(1);
		Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
		assertThat(payment.getId()).isEqualTo(paymentIdAfterFailure);
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
		assertBalance(CUSTOMER_ID, 5_000);
		assertBalance(cashWalletProperties.holdingMemberId(), 15_000);
		assertLedgerConsistency(CUSTOMER_ID, cashWalletProperties.holdingMemberId());
	}

	@Test
	@DisplayName("P4: COMPLETED 상태인 주문에 다시 결제 요청하면 차감 없이 PAID만 돌아온다")
	void p4_retryCompletedOrder_noDoubleDebit() {
		long orderId = 400;
		long amount = 3_000;
		createCustomerWallet(10_000);
		createHoldingWallet();

		cashFacade.completeOrderPayment(orderId, CUSTOMER_ID, amount);
		CashPaymentResponseDto second = cashFacade.completeOrderPayment(orderId, CUSTOMER_ID, amount);

		assertThat(second.outcome()).isEqualTo(CashPaymentResponseDto.Outcome.PAID);
		assertBalance(CUSTOMER_ID, 7_000);
		assertBalance(cashWalletProperties.holdingMemberId(), 3_000);
		assertThat(countLedgersForOrder(orderId)).isEqualTo(2);
		assertLedgerConsistency(CUSTOMER_ID, cashWalletProperties.holdingMemberId());
		assertTotalPreserved(10_000);
	}

	@Test
	@DisplayName("P5-1: CANCELED 상태인 결제 행에 다시 결제 요청하면 예외가 나고 차감되지 않는다")
	void p5_canceledPayment_throwsAndNoDebit() {
		assertFinalizedPaymentRejectsRetry(PaymentStatus.CANCELED, 501);
	}

	@Test
	@DisplayName("P5-2: REFUNDED 상태인 결제 행에 다시 결제 요청하면 예외가 나고 차감되지 않는다")
	void p5_refundedPayment_throwsAndNoDebit() {
		assertFinalizedPaymentRejectsRetry(PaymentStatus.REFUNDED, 502);
	}

	@Test
	@DisplayName("P6: 다른 회원 ID로 기존 결제 행에 결제를 시도하면 예외가 나고 어느 지갑도 차감되지 않는다")
	void p6_differentMemberIdOnExistingPayment_shouldBeRejected() {
		long orderId = 600;
		long amount = 3_000;
		long otherMemberId = 4;
		Wallet customerWallet = createCustomerWallet(10_000);
		createHoldingWallet();
		createWallet(otherMemberId, "다른고객", 10_000);

		Payment existing = paymentRepository.save(new Payment(orderId, customerWallet, amount));
		ReflectionTestUtils.setField(existing, "status", PaymentStatus.FAILED);
		paymentRepository.saveAndFlush(existing);

		Throwable thrown = catchThrowable(() -> cashFacade.completeOrderPayment(orderId, otherMemberId, amount));

		String thrownSummary = thrown == null ? "없음" : thrown.getClass().getName() + ": " + thrown.getMessage();
		long customerBalanceAfter = walletRepository.findById(CUSTOMER_ID).orElseThrow().getBalance();
		long otherBalanceAfter = walletRepository.findById(otherMemberId).orElseThrow().getBalance();
		Payment reloaded = paymentRepository.findByOrderId(orderId).orElseThrow();
		System.out.println("P6 예외: " + thrownSummary);
		System.out.println("P6 회원" + CUSTOMER_ID + " 잔액: " + customerBalanceAfter);
		System.out.println("P6 회원" + otherMemberId + " 잔액: " + otherBalanceAfter);
		System.out.println("P6 결제 행 상태: " + reloaded.getStatus() + ", wallet FK=" + reloaded.getWallet().getId());

		assertThat(thrown)
			.as("원래 주인(회원 %d)이 아닌 회원 %d가 같은 주문 ID로 결제하면 거절돼야 한다", CUSTOMER_ID, otherMemberId)
			.isNotNull();
		assertThat(walletRepository.findById(CUSTOMER_ID).orElseThrow().getBalance())
			.as("원래 고객(회원 %d) 지갑은 차감되면 안 된다", CUSTOMER_ID)
			.isEqualTo(10_000);
		assertThat(walletRepository.findById(otherMemberId).orElseThrow().getBalance())
			.as("다른 회원(회원 %d) 지갑도 차감되면 안 된다", otherMemberId)
			.isEqualTo(10_000);
		assertLedgerConsistency(CUSTOMER_ID, otherMemberId, cashWalletProperties.holdingMemberId());
	}

	@Test
	@DisplayName("P7: 같은 주문으로 10개 스레드가 동시에 결제해도 차감은 한 번만 일어난다")
	void p7_concurrentSameOrderId_debitsOnlyOnce() throws InterruptedException {
		long orderId = 700;
		long amount = 3_000;
		createCustomerWallet(10_000);
		createHoldingWallet();

		List<AttemptResult> results = runConcurrently(10, i -> () ->
			cashFacade.completeOrderPayment(orderId, CUSTOMER_ID, amount));

		long paidCount = results.stream().filter(result -> result.dto() != null
			&& result.dto().outcome() == CashPaymentResponseDto.Outcome.PAID).count();
		long errorCount = results.stream().filter(result -> result.error() != null).count();

		printAttemptSummary("P7", results);

		assertThat(errorCount).as("예외 없이 전부 응답해야 한다").isZero();
		assertThat(paidCount).as("10개 요청 모두 PAID를 받아야 한다(멱등)").isEqualTo(10);
		assertBalance(CUSTOMER_ID, 7_000);
		assertBalance(cashWalletProperties.holdingMemberId(), 3_000);
		assertThat(countLedgersForOrder(orderId)).as("장부는 차감 1줄 + 적립 1줄만 있어야 한다").isEqualTo(2);
		assertLedgerConsistency(CUSTOMER_ID, cashWalletProperties.holdingMemberId());
		assertTotalPreserved(10_000);
	}

	@Test
	@DisplayName("P8: 같은 지갑에서 서로 다른 주문 10개를 동시에 결제하면 잔액 한도 안에서만 성공한다")
	void p8_concurrentDifferentOrders_sameWallet_noOverdraft() throws InterruptedException {
		long baseOrderId = 800;
		long amount = 10_000;
		createCustomerWallet(50_000);
		createHoldingWallet();

		List<AttemptResult> results = runConcurrently(10, i -> () ->
			cashFacade.completeOrderPayment(baseOrderId + i + 1, CUSTOMER_ID, amount));

		long paidCount = results.stream().filter(result -> result.dto() != null
			&& result.dto().outcome() == CashPaymentResponseDto.Outcome.PAID).count();
		long insufficientCount = results.stream().filter(result -> result.dto() != null
			&& result.dto().outcome() == CashPaymentResponseDto.Outcome.INSUFFICIENT_BALANCE).count();
		long errorCount = results.stream().filter(result -> result.error() != null).count();
		long finalBalance = walletRepository.findById(CUSTOMER_ID).orElseThrow().getBalance();

		printAttemptSummary("P8", results);
		System.out.println("P8 최종 고객 잔액: " + finalBalance);

		assertThat(errorCount).as("예외 없이 전부 응답해야 한다").isZero();
		assertThat(paidCount).as("5개 주문만 성공해야 한다").isEqualTo(5);
		assertThat(insufficientCount).as("나머지 5개는 잔액 부족이어야 한다").isEqualTo(5);
		assertThat(finalBalance).as("잔액은 정확히 0이어야 한다").isZero();
		assertThat(finalBalance).as("잔액이 음수가 되면 안 된다").isNotNegative();
		assertLedgerConsistency(CUSTOMER_ID, cashWalletProperties.holdingMemberId());
		assertTotalPreserved(50_000);
	}

	@Test
	@DisplayName("P9: 고객 차감 직후 보관 지갑 적립 저장이 실패하면 고객 차감과 장부가 모두 롤백된다")
	void p9_failureAfterCustomerDebit_rollsBackEverything() {
		long orderId = 900;
		long amount = 3_000;
		createCustomerWallet(10_000);
		createHoldingWallet();

		doThrow(new RuntimeException("강제 실패(테스트용): 보관 지갑 적립 저장 실패"))
			.when(walletLedgerRepository)
			.save(argThat((WalletLedger ledger) -> ledger != null && ledger.getLedgerType() == LedgerType.임시보관__주문결제));

		assertThatThrownBy(() -> cashFacade.completeOrderPayment(orderId, CUSTOMER_ID, amount))
			.isInstanceOf(RuntimeException.class)
			.hasMessageContaining("강제 실패");

		assertBalance(CUSTOMER_ID, 10_000);
		assertBalance(cashWalletProperties.holdingMemberId(), 0);
		assertThat(countLedgersForOrder(orderId)).as("둘 다 롤백되어 장부가 하나도 없어야 한다").isZero();
		assertThat(paymentRepository.findByOrderId(orderId)).as("결제 행도 롤백되어야 한다").isEmpty();
		assertLedgerConsistency(CUSTOMER_ID, cashWalletProperties.holdingMemberId());
		assertTotalPreserved(10_000);
	}

	private void assertFinalizedPaymentRejectsRetry(PaymentStatus status, long orderId) {
		long amount = 3_000;
		Wallet customerWallet = createCustomerWallet(10_000);
		createHoldingWallet();
		Payment payment = paymentRepository.save(new Payment(orderId, customerWallet, amount));
		ReflectionTestUtils.setField(payment, "status", status);
		paymentRepository.saveAndFlush(payment);

		assertThatThrownBy(() -> cashFacade.completeOrderPayment(orderId, CUSTOMER_ID, amount))
			.isInstanceOf(PaymentAlreadyFinalizedException.class);

		assertBalance(CUSTOMER_ID, 10_000);
		assertThat(countLedgersForOrder(orderId)).isZero();
		assertLedgerConsistency(CUSTOMER_ID, cashWalletProperties.holdingMemberId());
	}

	private List<AttemptResult> runConcurrently(int threadCount, IntFunction<Callable<CashPaymentResponseDto>> taskFactory)
		throws InterruptedException {
		ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		CountDownLatch startSignal = new CountDownLatch(1);
		List<Future<CashPaymentResponseDto>> futures = new ArrayList<>();
		for (int i = 0; i < threadCount; i++) {
			Callable<CashPaymentResponseDto> task = taskFactory.apply(i);
			futures.add(executor.submit(() -> {
				startSignal.await();
				return task.call();
			}));
		}
		startSignal.countDown();

		List<AttemptResult> results = new ArrayList<>();
		for (Future<CashPaymentResponseDto> future : futures) {
			try {
				results.add(new AttemptResult(future.get(), null));
			} catch (ExecutionException e) {
				results.add(new AttemptResult(null, e.getCause()));
			}
		}
		executor.shutdown();
		return results;
	}

	private void printAttemptSummary(String label, List<AttemptResult> results) {
		for (int i = 0; i < results.size(); i++) {
			AttemptResult attempt = results.get(i);
			if (attempt.error() != null) {
				System.out.printf("%s #%d 예외: %s: %s%n",
					label, i, attempt.error().getClass().getName(), attempt.error().getMessage());
			} else {
				System.out.printf("%s #%d 결과: %s (shortage=%d)%n",
					label, i, attempt.dto().outcome(), attempt.dto().shortage());
			}
		}
	}

	private long countLedgersForOrder(long orderId) {
		return walletLedgerRepository.findAll().stream()
			.filter(ledger -> ledger.getRefTypeCode() == LedgerRefType.Order && ledger.getRefId() == orderId)
			.count();
	}

	private long ledgerAmountSumForOrder(long orderId) {
		return walletLedgerRepository.findAll().stream()
			.filter(ledger -> ledger.getRefTypeCode() == LedgerRefType.Order && ledger.getRefId() == orderId)
			.mapToLong(WalletLedger::getAmount)
			.sum();
	}

	private void assertBalance(long holderId, int expected) {
		assertThat(walletRepository.findById(holderId).orElseThrow().getBalance()).isEqualTo(expected);
	}

	// 각 지갑의 잔액은 그 지갑 장부 금액의 합과 같아야 한다
	private void assertLedgerConsistency(long... holderIds) {
		for (long holderId : holderIds) {
			long walletBalance = walletRepository.findById(holderId).orElseThrow().getBalance();
			long ledgerSum = walletLedgerRepository.findAll().stream()
				.filter(ledger -> ledger.getWallet().getId() == holderId)
				.mapToLong(WalletLedger::getAmount)
				.sum();
			assertThat(walletBalance).as("지갑 %d 잔액은 그 지갑 장부 합과 같아야 한다", holderId).isEqualTo(ledgerSum);
		}
	}

	// 고객 지갑 + 보관 지갑 잔액 합은 테스트 전(충전된 총액)과 같아야 한다
	private void assertTotalPreserved(int expectedTotal) {
		long customerBalance = walletRepository.findById(CUSTOMER_ID).orElseThrow().getBalance();
		long holdingBalance = walletRepository.findById(cashWalletProperties.holdingMemberId())
			.orElseThrow().getBalance();
		assertThat(customerBalance + holdingBalance).as("고객+보관 잔액 합이 보존되어야 한다").isEqualTo(expectedTotal);
	}

	private Wallet createCustomerWallet(long initialBalance) {
		return createWallet(CUSTOMER_ID, "러너3", initialBalance);
	}

	private void createHoldingWallet() {
		createWallet(cashWalletProperties.holdingMemberId(), "홀딩", 0);
	}

	private Wallet createWallet(long memberId, String nickname, long initialBalance) {
		return transactionTemplate.execute(status -> {
			LocalDateTime now = LocalDateTime.of(2026, 10, 12, 10, 0);
			CashMember member = cashMemberRepository.save(new CashMember(memberId, now, now, nickname));
			Wallet wallet = walletRepository.save(new Wallet(member));
			if (initialBalance != 0) {
				walletLedgerRepository.save(
					wallet.credit(initialBalance, LedgerType.충전__PG결제_토스페이먼츠, LedgerRefType.WalletCharge, memberId));
			}
			return wallet;
		});
	}

	private record AttemptResult(CashPaymentResponseDto dto, Throwable error) {
	}
}
