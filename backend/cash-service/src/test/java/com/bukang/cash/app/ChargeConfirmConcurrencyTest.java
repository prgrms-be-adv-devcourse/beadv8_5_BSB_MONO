package com.bukang.cash.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mockingDetails;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntFunction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.Invocation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.cash.domain.exception.PaymentApprovalFailedException;
import com.bukang.cash.domain.member.CashMember;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.dto.CashChargeResponseDto;
import com.bukang.cash.out.CashMemberRepository;
import com.bukang.cash.out.WalletChargeRepository;
import com.bukang.cash.out.WalletLedgerRepository;
import com.bukang.cash.out.WalletRepository;
import com.bukang.cash.out.pg.PaymentApprovalResult;
import com.bukang.cash.out.pg.PaymentClient;

// 같은 orderId로 충전 confirm이 동시에 여러 번 와도 중복 적립 없이 하나의 결과로 수렴하는지,
// 그리고 PaymentClient.approve가 실제로 몇 번 불리는지 확인한다 (완료 기준: "한 번만 불리고"를 전제함)
@SpringBootTest
@ActiveProfiles("test")
class ChargeConfirmConcurrencyTest {
	private static final long CUSTOMER_ID = 3;

	@Autowired
	private CashFacade cashFacade;
	@Autowired
	private CashMemberRepository cashMemberRepository;
	@Autowired
	private WalletRepository walletRepository;
	@Autowired
	private WalletChargeRepository walletChargeRepository;
	@Autowired
	private WalletLedgerRepository walletLedgerRepository;
	@Autowired
	private TransactionTemplate transactionTemplate;

	@MockitoBean
	private PaymentClient paymentClient;

	@AfterEach
	void tearDown() {
		walletChargeRepository.deleteAll();
		walletLedgerRepository.deleteAll();
		walletRepository.deleteAll();
		cashMemberRepository.deleteAll();
	}

	@Test
	@DisplayName("같은 orderId로 충전 confirm이 동시에 여러 번 와도 중복 적립 없이 하나의 결과로 수렴한다")
	void concurrentSameOrderIdChargeConfirm_convergesToSingleResult() throws InterruptedException {
		createCustomerWallet();
		given(paymentClient.approve(anyString(), anyString(), anyLong()))
			.willReturn(new PaymentApprovalResult(10_000, "toss_key_1"));

		List<AttemptResult> results = runConcurrently(10, i ->
			() -> cashFacade.confirmWalletCharge(CUSTOMER_ID, "pay_1", "order_1", 10_000));

		long completedCount = results.stream().filter(r -> r.dto() != null
			&& r.dto().outcome() == CashChargeResponseDto.Outcome.COMPLETED).count();
		long errorCount = results.stream().filter(r -> r.error() != null).count();
		long approveInvocationCount = mockingDetails(paymentClient).getInvocations().stream()
			.map(Invocation::getMethod)
			.filter(method -> method.getName().equals("approve"))
			.count();

		printAttemptSummary(results);
		System.out.println("paymentClient.approve 호출 횟수: " + approveInvocationCount);

		assertThat(errorCount).as("예외 없이 전부 응답해야 한다").isZero();
		assertThat(completedCount).as("10개 요청 모두 COMPLETED를 받아야 한다(멱등)").isEqualTo(10);
		assertThat(approveInvocationCount).as("IN_PROGRESS 선점 덕분에 Toss는 한 번만 불려야 한다").isEqualTo(1);
		assertBalance(CUSTOMER_ID, 10_000);
		assertThat(walletChargeRepository.count()).as("충전 기록은 하나만 남아야 한다").isEqualTo(1);
		assertThat(walletLedgerRepository.findAll()).as("장부도 한 줄만 남아야 한다").hasSize(1);
	}

	@Test
	@DisplayName("IN_PROGRESS 선점으로 애초에 Toss를 한 번만 부르며 예외 없이 COMPLETED로 수렴한다")
	void concurrentSameOrderIdChargeConfirm_withRealisticTossRejection_stillConvergesSafely()
		throws InterruptedException {
		createCustomerWallet();
		AtomicBoolean alreadyApproved = new AtomicBoolean(false);
		given(paymentClient.approve(anyString(), anyString(), anyLong())).willAnswer(invocation -> {
			if (alreadyApproved.compareAndSet(false, true)) {
				return new PaymentApprovalResult(10_000, "toss_key_1");
			}
			throw new PaymentApprovalFailedException("이미 처리가 끝난 결제 입니다.", "ALREADY_PROCESSED_PAYMENT");
		});
		given(paymentClient.findCompletedByOrderId("order_1")).willAnswer(invocation ->
			alreadyApproved.get()
				? Optional.of(new PaymentApprovalResult(10_000, "toss_key_1"))
				: Optional.empty());

		List<AttemptResult> results = runConcurrently(10, i ->
			() -> cashFacade.confirmWalletCharge(CUSTOMER_ID, "pay_1", "order_1", 10_000));

		long completedCount = results.stream().filter(r -> r.dto() != null
			&& r.dto().outcome() == CashChargeResponseDto.Outcome.COMPLETED).count();
		long errorCount = results.stream().filter(r -> r.error() != null).count();
		long approveInvocationCount = mockingDetails(paymentClient).getInvocations().stream()
			.map(Invocation::getMethod)
			.filter(method -> method.getName().equals("approve"))
			.count();

		printAttemptSummary(results);

		assertThat(errorCount).as("Toss 거절이 섞여도 예외 없이 전부 응답해야 한다").isZero();
		assertThat(completedCount).as("10개 요청 모두 COMPLETED를 받아야 한다").isEqualTo(10);
		assertThat(approveInvocationCount)
			.as("IN_PROGRESS 선점 덕분에 ALREADY_PROCESSED_PAYMENT 거절 분기를 탈 일 없이 한 번만 불려야 한다")
			.isEqualTo(1);
		assertBalance(CUSTOMER_ID, 10_000);
		assertThat(walletChargeRepository.count()).as("충전 기록은 하나만 남아야 한다").isEqualTo(1);
		assertThat(walletLedgerRepository.findAll()).as("장부도 한 줄만 남아야 한다").hasSize(1);
	}


	@Test
	@DisplayName("같은 orderId로 충전 confirm이 동시에 여러 번 왔는데 Toss가 거절하면, "
		+ "Toss는 한 번만 불리고 전부 FAILED로 수렴한다")
	void concurrentSameOrderIdChargeConfirm_whenTossRejects_convergesToFailedForAll() throws InterruptedException {
		createCustomerWallet();
		given(paymentClient.approve(anyString(), anyString(), anyLong())).willAnswer(invocation -> {
			Thread.sleep(300);
			throw new PaymentApprovalFailedException("카드 한도를 초과했습니다.", "EXCEED_MAX_DAILY_PAYMENT_COUNT");
		});

		List<AttemptResult> results = runConcurrently(10, i ->
			() -> cashFacade.confirmWalletCharge(CUSTOMER_ID, "pay_1", "order_1", 10_000));

		long failedCount = results.stream().filter(r -> r.dto() != null
			&& r.dto().outcome() == CashChargeResponseDto.Outcome.FAILED).count();
		long errorCount = results.stream().filter(r -> r.error() != null).count();
		long approveInvocationCount = mockingDetails(paymentClient).getInvocations().stream()
			.map(Invocation::getMethod)
			.filter(method -> method.getName().equals("approve"))
			.count();

		printAttemptSummary(results);

		assertThat(errorCount).as("예외 없이 전부 응답해야 한다").isZero();
		assertThat(failedCount).as("10개 요청 모두 FAILED를 받아야 한다").isEqualTo(10);
		assertThat(approveInvocationCount).as("IN_PROGRESS 선점 덕분에 Toss는 한 번만 불려야 한다").isEqualTo(1);
		assertBalance(CUSTOMER_ID, 0);
		assertThat(walletChargeRepository.count()).as("충전 기록은 하나만 남아야 한다").isEqualTo(1);
		assertThat(walletLedgerRepository.findAll()).as("적립 장부는 생기지 않아야 한다").isEmpty();
	}

	private List<AttemptResult> runConcurrently(
		int threadCount, IntFunction<Callable<CashChargeResponseDto>> taskFactory
	) throws InterruptedException {
		ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		CountDownLatch startSignal = new CountDownLatch(1);
		List<Future<CashChargeResponseDto>> futures = new ArrayList<>();
		for (int i = 0; i < threadCount; i++) {
			Callable<CashChargeResponseDto> task = taskFactory.apply(i);
			futures.add(executor.submit(() -> {
				startSignal.await();
				return task.call();
			}));
		}
		startSignal.countDown();

		List<AttemptResult> results = new ArrayList<>();
		for (Future<CashChargeResponseDto> future : futures) {
			try {
				results.add(new AttemptResult(future.get(), null));
			} catch (ExecutionException e) {
				results.add(new AttemptResult(null, e.getCause()));
			}
		}
		executor.shutdown();
		return results;
	}

	private void printAttemptSummary(List<AttemptResult> results) {
		for (int i = 0; i < results.size(); i++) {
			AttemptResult attempt = results.get(i);
			if (attempt.error() != null) {
				System.out.printf("#%d 예외: %s: %s%n",
					i, attempt.error().getClass().getName(), attempt.error().getMessage());
			} else {
				System.out.printf("#%d 결과: %s%n", i, attempt.dto().outcome());
			}
		}
	}

	private void createCustomerWallet() {
		transactionTemplate.executeWithoutResult(status -> {
			LocalDateTime now = LocalDateTime.of(2026, 10, 18, 10, 0);
			CashMember member = cashMemberRepository.save(new CashMember(CUSTOMER_ID, now, now, "러너" + CUSTOMER_ID));
			walletRepository.save(new Wallet(member));
		});
	}

	private void assertBalance(long holderId, int expected) {
		assertThat(walletRepository.findById(holderId).orElseThrow().getBalance()).isEqualTo(expected);
	}

	private record AttemptResult(CashChargeResponseDto dto, Throwable error) {
	}
}
