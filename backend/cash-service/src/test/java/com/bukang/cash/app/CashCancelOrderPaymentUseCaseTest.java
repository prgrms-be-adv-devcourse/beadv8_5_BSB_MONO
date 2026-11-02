package com.bukang.cash.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.cash.config.CashWalletProperties;
import com.bukang.cash.domain.exception.PaymentMemberMismatchException;
import com.bukang.cash.domain.member.CashMember;
import com.bukang.cash.domain.payment.Payment;
import com.bukang.cash.domain.payment.PaymentStatus;
import com.bukang.cash.domain.wallet.LedgerRefType;
import com.bukang.cash.domain.wallet.LedgerType;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.domain.wallet.WalletLedger;
import com.bukang.cash.out.CashMemberRepository;
import com.bukang.cash.out.PaymentRepository;
import com.bukang.cash.out.WalletLedgerRepository;
import com.bukang.cash.out.WalletRepository;

// PRO-80 완료 기준 중 동시성·REFUNDED 분기는 MockMvc 없이 Facade를 직접 불러 확인한다
// (CashCompleteOrderPaymentUseCaseTest의 P7/P8과 같은 방식)
@SpringBootTest
@ActiveProfiles("test")
class CashCancelOrderPaymentUseCaseTest {
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
	private WalletLedgerRepository walletLedgerRepository;
	@Autowired
	private PaymentRepository paymentRepository;
	@Autowired
	private TransactionTemplate transactionTemplate;

	@AfterEach
	void tearDown() {
		paymentRepository.deleteAll();
		walletLedgerRepository.deleteAll();
		walletRepository.deleteAll();
		cashMemberRepository.deleteAll();
	}

	@Test
	@DisplayName("이미 REFUNDED인 결제에 환원을 요청해도 아무것도 바뀌지 않고 성공한다")
	void cancelRefundedPayment_noDoubleRefund() {
		long orderId = 200;
		long amount = 3_000;
		Wallet customerWallet = createCustomerWallet(10_000);
		createHoldingWallet();
		Payment payment = paymentRepository.save(new Payment(orderId, customerWallet, amount));
		ReflectionTestUtils.setField(payment, "status", PaymentStatus.REFUNDED);
		paymentRepository.saveAndFlush(payment);

		cashFacade.cancelPayment(orderId, CUSTOMER_ID);

		assertBalance(CUSTOMER_ID, 10_000);
		assertThat(countLedgersForOrder(orderId)).as("환원은 아무 장부도 남기면 안 된다").isZero();
		assertThat(paymentRepository.findByOrderId(orderId).orElseThrow().getStatus())
			.as("REFUNDED는 그대로 유지돼야 한다(CANCELED로 덮어쓰면 안 됨)")
			.isEqualTo(PaymentStatus.REFUNDED);
	}

	@Test
	@DisplayName("결제한 지갑과 다른 회원 ID로 환원을 요청하면 거절되고, 어느 지갑도 바뀌지 않는다")
	void cancelPaymentRejectsMemberMismatch() {
		long orderId = 250;
		long amount = 3_000;
		long otherMemberId = 4;
		createCustomerWallet(10_000);
		createHoldingWallet();
		createWallet(otherMemberId, "다른고객", 10_000);
		cashFacade.completeOrderPayment(orderId, CUSTOMER_ID, amount);

		assertThatThrownBy(() -> cashFacade.cancelPayment(orderId, otherMemberId))
			.isInstanceOf(PaymentMemberMismatchException.class);

		assertBalance(CUSTOMER_ID, 7_000);
		assertBalance(cashWalletProperties.holdingMemberId(), 3_000);
		assertBalance(otherMemberId, 10_000);
		assertThat(paymentRepository.findByOrderId(orderId).orElseThrow().getStatus())
			.as("결제 상태도 그대로 COMPLETED여야 한다")
			.isEqualTo(PaymentStatus.COMPLETED);
	}

	@Test
	@DisplayName("같은 주문으로 10개 스레드가 동시에 환원을 요청해도 반환은 한 번만 일어난다")
	void concurrentCancelSameOrder_refundsOnlyOnce() throws InterruptedException {
		long orderId = 300;
		long amount = 3_000;
		createCustomerWallet(10_000);
		createHoldingWallet();
		cashFacade.completeOrderPayment(orderId, CUSTOMER_ID, amount);

		List<Throwable> errors = runConcurrently(10, () -> cashFacade.cancelPayment(orderId, CUSTOMER_ID));

		assertThat(errors).as("예외 없이 전부 처리돼야 한다").isEmpty();
		assertBalance(CUSTOMER_ID, 10_000);
		assertBalance(cashWalletProperties.holdingMemberId(), 0);
		assertThat(countLedgersForOrder(orderId)).as("결제 2줄 + 환원 2줄만 있어야 한다").isEqualTo(4);
		assertThat(paymentRepository.findByOrderId(orderId).orElseThrow().getStatus())
			.isEqualTo(PaymentStatus.CANCELED);
	}

	@Test
	@DisplayName("같은 주문에 결제와 환원이 동시에 와도 최종 잔액과 장부는 맞아떨어진다")
	void concurrentPayAndCancel_endsConsistent() throws InterruptedException {
		long orderId = 400;
		long amount = 3_000;
		createCustomerWallet(10_000);
		createHoldingWallet();

		CountDownLatch startSignal = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		Future<?> payFuture = executor.submit(() -> {
			await(startSignal);
			try {
				cashFacade.completeOrderPayment(orderId, CUSTOMER_ID, amount);
			} catch (RuntimeException ignored) {
				// 취소가 먼저 선점했다면 PaymentAlreadyFinalizedException이 날 수 있다 (의도된 결과)
			}
		});
		Future<?> cancelFuture = executor.submit(() -> {
			await(startSignal);
			cashFacade.cancelPayment(orderId, CUSTOMER_ID);
		});
		startSignal.countDown();
		join(payFuture);
		join(cancelFuture);
		executor.shutdown();

		// 어느 순서로 섞이든, 결제와 환원이 둘 다 끝나면 최종 상태는 항상 취소다
		assertThat(paymentRepository.findByOrderId(orderId).orElseThrow().getStatus())
			.isEqualTo(PaymentStatus.CANCELED);
		assertTotalPreserved(10_000);
		assertLedgerConsistency(CUSTOMER_ID, cashWalletProperties.holdingMemberId());
	}

	private List<Throwable> runConcurrently(int threadCount, Runnable task) throws InterruptedException {
		ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		CountDownLatch startSignal = new CountDownLatch(1);
		List<Future<Void>> futures = new ArrayList<>();
		for (int i = 0; i < threadCount; i++) {
			Callable<Void> callable = () -> {
				startSignal.await();
				task.run();
				return null;
			};
			futures.add(executor.submit(callable));
		}
		startSignal.countDown();

		List<Throwable> errors = new ArrayList<>();
		for (Future<Void> future : futures) {
			try {
				future.get();
			} catch (ExecutionException e) {
				errors.add(e.getCause());
			}
		}
		executor.shutdown();
		return errors;
	}

	private void await(CountDownLatch latch) {
		try {
			latch.await();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}

	private void join(Future<?> future) {
		try {
			future.get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (ExecutionException ignored) {
			// 각 작업 안에서 이미 처리했으므로 여기서는 무시한다
		}
	}

	private void assertBalance(long holderId, int expected) {
		assertThat(walletRepository.findById(holderId).orElseThrow().getBalance()).isEqualTo(expected);
	}

	private long countLedgersForOrder(long orderId) {
		return walletLedgerRepository.findAll().stream()
			.filter(ledger -> ledger.getRefTypeCode() == LedgerRefType.Order && ledger.getRefId() == orderId)
			.count();
	}

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
}
