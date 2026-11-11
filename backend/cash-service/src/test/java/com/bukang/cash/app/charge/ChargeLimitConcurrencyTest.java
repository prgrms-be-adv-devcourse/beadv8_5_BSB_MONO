package com.bukang.cash.app.charge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mockingDetails;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.cash.RedisTestcontainersConfiguration;
import com.bukang.cash.app.CashFacade;
import com.bukang.cash.domain.exception.charge.ChargeAmountTooLargeException;
import com.bukang.cash.domain.member.CashMember;
import com.bukang.cash.domain.wallet.LedgerRefType;
import com.bukang.cash.domain.wallet.LedgerType;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.dto.charge.CashChargeResponseDto;
import com.bukang.cash.out.charge.WalletChargeRepository;
import com.bukang.cash.out.member.CashMemberRepository;
import com.bukang.cash.out.pg.PaymentApprovalResult;
import com.bukang.cash.out.pg.PaymentClient;
import com.bukang.cash.out.wallet.WalletLedgerRepository;
import com.bukang.cash.out.wallet.WalletRepository;

// 주문번호가 다른 충전 두 건이 동시에 확정될 때, 먼저 처리 중이 된 충전 금액도 한도에 세어 뒤 충전을 막는지 확인한다.
// 토스 승인을 늦춰 두 요청이 모두 처리 중인 순간을 만든다
@SpringBootTest
@ActiveProfiles("test")
@Import(RedisTestcontainersConfiguration.class)
class ChargeLimitConcurrencyTest {
	private static final long CUSTOMER_ID = 33;
	private static final long CHARGE_AMOUNT = 100_000;

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
	@Autowired
	private StringRedisTemplate redisTemplate;

	@MockitoBean
	private PaymentClient paymentClient;

	@AfterEach
	void tearDown() {
		walletChargeRepository.deleteAll();
		walletLedgerRepository.deleteAll();
		walletRepository.deleteAll();
		cashMemberRepository.deleteAll();
		redisTemplate.execute((RedisCallback<Void>)connection -> {
			connection.serverCommands().flushDb();
			return null;
		});
	}

	@Test
	@DisplayName("오늘 850,000원을 충전한 회원이 다른 주문번호로 100,000원 두 건을 동시에 확정하면 한 건만 통과한다")
	void concurrentChargesWithDifferentOrderIds_doNotExceedDailyLimit() throws InterruptedException {
		createCustomerWallet(850_000, LedgerType.충전__PG결제_토스페이먼츠);
		givenSlowApproval();

		List<Attempt> attempts = confirmTwoChargesAtOnce();

		printSummary(attempts);
		assertOnlyOnePassed(attempts, ChargeAmountTooLargeException.PER_DAY_CODE);
		assertThat(todayChargeTotal()).as("오늘 충전 합계가 일일 한도 1,000,000원을 넘으면 안 된다")
			.isLessThanOrEqualTo(1_000_000);
	}

	@Test
	@DisplayName("잔액이 9,850,000원인 회원이 다른 주문번호로 100,000원 두 건을 동시에 확정하면 한 건만 통과한다")
	void concurrentChargesWithDifferentOrderIds_doNotExceedBalanceCap() throws InterruptedException {
		// 충전이 아닌 돈으로 잔액을 채워 하루 한도에 먼저 걸리지 않게 한다
		createCustomerWallet(9_850_000, LedgerType.환불__주문취소);
		givenSlowApproval();

		List<Attempt> attempts = confirmTwoChargesAtOnce();

		printSummary(attempts);
		assertOnlyOnePassed(attempts, ChargeAmountTooLargeException.BALANCE_CODE);
		assertThat(walletRepository.findById(CUSTOMER_ID).orElseThrow().getBalance())
			.as("잔액이 보유 한도 10,000,000원을 넘으면 안 된다")
			.isLessThanOrEqualTo(10_000_000);
	}

	private void givenSlowApproval() {
		given(paymentClient.approve(anyString(), anyString(), anyLong())).willAnswer(invocation -> {
			Thread.sleep(500);
			return new PaymentApprovalResult(CHARGE_AMOUNT, "toss_" + invocation.getArgument(1), null);
		});
	}

	private List<Attempt> confirmTwoChargesAtOnce() throws InterruptedException {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch startSignal = new CountDownLatch(1);
		List<Future<CashChargeResponseDto>> futures = new ArrayList<>();
		for (String orderId : List.of("order_a", "order_b")) {
			futures.add(executor.submit(() -> {
				startSignal.await();
				return cashFacade.confirmWalletCharge(CUSTOMER_ID, "pay_" + orderId, orderId, CHARGE_AMOUNT);
			}));
		}
		startSignal.countDown();

		List<Attempt> attempts = new ArrayList<>();
		for (Future<CashChargeResponseDto> future : futures) {
			try {
				attempts.add(new Attempt(future.get(), null));
			} catch (ExecutionException e) {
				attempts.add(new Attempt(null, e.getCause()));
			}
		}
		executor.shutdown();
		return attempts;
	}

	private void assertOnlyOnePassed(List<Attempt> attempts, String expectedCode) {
		long completedCount = attempts.stream()
			.filter(a -> a.dto() != null && a.dto().outcome() == CashChargeResponseDto.Outcome.COMPLETED)
			.count();
		List<String> rejectedCodes = attempts.stream()
			.filter(a -> a.error() instanceof ChargeAmountTooLargeException)
			.map(a -> ((ChargeAmountTooLargeException)a.error()).getCode())
			.toList();
		long approveCount = mockingDetails(paymentClient).getInvocations().stream()
			.filter(invocation -> invocation.getMethod().getName().equals("approve"))
			.count();

		assertThat(completedCount).as("한 건만 완료돼야 한다").isEqualTo(1);
		assertThat(rejectedCodes).as("나머지 한 건은 한도 초과로 거절돼야 한다").containsExactly(expectedCode);
		assertThat(approveCount).as("거절된 충전은 토스를 부르지 않아야 한다").isEqualTo(1);
	}

	private long todayChargeTotal() {
		return transactionTemplate.execute(status -> walletLedgerRepository.sumAmountSince(
			walletRepository.findById(CUSTOMER_ID).orElseThrow(), LedgerType.충전__PG결제_토스페이먼츠,
			LocalDate.now(ZoneId.of("Asia/Seoul")).atStartOfDay()));
	}

	private void printSummary(List<Attempt> attempts) {
		for (int i = 0; i < attempts.size(); i++) {
			Attempt attempt = attempts.get(i);
			System.out.printf("#%d %s%n", i, attempt.error() != null
				? "예외: " + attempt.error().getClass().getSimpleName() + ": " + attempt.error().getMessage()
				: "결과: " + attempt.dto().outcome());
		}
		Wallet wallet = walletRepository.findById(CUSTOMER_ID).orElseThrow();
		System.out.printf("잔액: %,d원, 오늘 충전 합계: %,d원%n", wallet.getBalance(), todayChargeTotal());
	}

	private void createCustomerWallet(long initialAmount, LedgerType ledgerType) {
		transactionTemplate.executeWithoutResult(status -> {
			LocalDateTime now = LocalDateTime.of(2026, 10, 18, 10, 0);
			CashMember member = cashMemberRepository.save(new CashMember(CUSTOMER_ID, now, now, "러너" + CUSTOMER_ID));
			Wallet wallet = walletRepository.save(new Wallet(member));
			walletLedgerRepository.save(wallet.credit(initialAmount, ledgerType, LedgerRefType.WalletCharge, 0L));
		});
	}

	private record Attempt(CashChargeResponseDto dto, Throwable error) {
	}
}
