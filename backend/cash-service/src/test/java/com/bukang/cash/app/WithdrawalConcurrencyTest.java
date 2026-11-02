package com.bukang.cash.app;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.cash.domain.exception.WalletBalanceEmptyException;
import com.bukang.cash.domain.member.CashMember;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.dto.CashWithdrawalResponseDto;
import com.bukang.cash.out.CashMemberRepository;
import com.bukang.cash.out.WalletLedgerRepository;
import com.bukang.cash.out.WalletRepository;
import com.bukang.cash.out.WalletWithdrawalRepository;

// PRO-86: 같은 지갑에 서로 다른 멱등키로 동시에 전액 환급을 신청해도 잔액이 음수가 되지 않는지 확인한다.
// CashWithdrawalUseCase가 지갑을 findByIdForUpdate로 먼저 잠그고 잔액을 확인하므로, 딱 한 건만 성공하고
// 나머지는 "잔액 없음"으로 거절돼야 한다 (잠근 뒤 확인하는 패턴이라 PRO-215·PRO-84와 같은 전례를 따른다).
@SpringBootTest
@ActiveProfiles("test")
class WithdrawalConcurrencyTest {
	private static final long CUSTOMER_ID = 3;

	@Autowired
	private CashFacade cashFacade;
	@Autowired
	private CashMemberRepository cashMemberRepository;
	@Autowired
	private WalletRepository walletRepository;
	@Autowired
	private WalletWithdrawalRepository walletWithdrawalRepository;
	@Autowired
	private WalletLedgerRepository walletLedgerRepository;
	@Autowired
	private TransactionTemplate transactionTemplate;

	@AfterEach
	void tearDown() {
		walletWithdrawalRepository.deleteAll();
		walletLedgerRepository.deleteAll();
		walletRepository.deleteAll();
		cashMemberRepository.deleteAll();
	}

	@Test
	@DisplayName("PRO-86: 같은 지갑에 서로 다른 멱등키로 동시에 전액 환급을 신청해도, "
		+ "딱 한 건만 성공하고 잔액은 0에서 멈춘다 (음수가 되지 않는다)")
	void concurrentWithdrawalsWithDifferentIdempotencyKeys_onlyOneSucceeds() throws InterruptedException {
		createCustomerWallet(10_000);

		List<AttemptResult> results = runConcurrently(10, i ->
			() -> cashFacade.requestWithdrawal(CUSTOMER_ID, "004", "110123456789", "홍길동", "idem-" + i));

		long successCount = results.stream().filter(r -> r.error() == null).count();
		long balanceEmptyCount = results.stream()
			.filter(r -> r.error() instanceof WalletBalanceEmptyException)
			.count();
		long otherErrorCount = results.size() - successCount - balanceEmptyCount;

		printAttemptSummary(results);

		assertThat(otherErrorCount).as("잔액 없음 말고 다른 예외는 없어야 한다").isZero();
		assertThat(successCount).as("딱 한 건만 성공해야 한다").isEqualTo(1);
		assertThat(balanceEmptyCount).as("나머지 9건은 잔액 없음으로 거절돼야 한다").isEqualTo(9);
		assertThat(walletRepository.findById(CUSTOMER_ID).orElseThrow().getBalance())
			.as("잔액은 정확히 0이어야 한다 (음수 불가)")
			.isZero();
		assertThat(walletWithdrawalRepository.count()).as("환급 기록도 하나만 남아야 한다").isEqualTo(1);
	}

	private List<AttemptResult> runConcurrently(
		int threadCount, IntFunction<Callable<CashWithdrawalResponseDto>> taskFactory
	) throws InterruptedException {
		ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		CountDownLatch startSignal = new CountDownLatch(1);
		List<Future<CashWithdrawalResponseDto>> futures = new ArrayList<>();
		for (int i = 0; i < threadCount; i++) {
			Callable<CashWithdrawalResponseDto> task = taskFactory.apply(i);
			futures.add(executor.submit(() -> {
				startSignal.await();
				return task.call();
			}));
		}
		startSignal.countDown();

		List<AttemptResult> results = new ArrayList<>();
		for (Future<CashWithdrawalResponseDto> future : futures) {
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
				System.out.printf("PRO-86(환급) #%d 예외: %s%n", i, attempt.error().getClass().getSimpleName());
			} else {
				System.out.printf("PRO-86(환급) #%d 결과: 성공, 잔액=%d%n", i, attempt.dto().balance());
			}
		}
	}

	private void createCustomerWallet(long initialBalance) {
		transactionTemplate.executeWithoutResult(status -> {
			LocalDateTime now = LocalDateTime.of(2026, 10, 18, 10, 0);
			CashMember member = cashMemberRepository.save(new CashMember(CUSTOMER_ID, now, now, "러너" + CUSTOMER_ID));
			Wallet wallet = new Wallet(member);
			ReflectionTestUtils.setField(wallet, "balance", initialBalance);
			walletRepository.save(wallet);
		});
	}

	private record AttemptResult(CashWithdrawalResponseDto dto, Throwable error) {
	}
}
