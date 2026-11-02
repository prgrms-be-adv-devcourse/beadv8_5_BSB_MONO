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
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.cash.config.CashWalletProperties;
import com.bukang.cash.domain.member.CashMember;
import com.bukang.cash.domain.payout.PayoutStage;
import com.bukang.cash.domain.seller.CashSeller;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.dto.CashPayoutResponseDto;
import com.bukang.cash.out.CashMemberRepository;
import com.bukang.cash.out.CashSellerRepository;
import com.bukang.cash.out.PayoutTransferRepository;
import com.bukang.cash.out.WalletLedgerRepository;
import com.bukang.cash.out.WalletRepository;

// PRO-256: 같은 정산 ID로 정산 지급 요청이 동시에 여러 번 와도 지갑 잔액이 한 번만 바뀌는지 확인한다
// sellerId는 판매자 신청서 ID(SELLER_APPLICATION_ID)이고, 실제 지갑 주인은 회원 ID(SELLER_MEMBER_ID)로 서로 다르다
@SpringBootTest
@ActiveProfiles("test")
class PayoutConcurrencyTest {
	private static final long SELLER_APPLICATION_ID = 50;
	private static final long SELLER_MEMBER_ID = 10;
	private static final long PAYOUT_ID = 900;

	@Autowired
	private CashFacade cashFacade;
	@Autowired
	private CashWalletProperties cashWalletProperties;
	@Autowired
	private CashMemberRepository cashMemberRepository;
	@Autowired
	private CashSellerRepository cashSellerRepository;
	@Autowired
	private WalletRepository walletRepository;
	@Autowired
	private WalletLedgerRepository walletLedgerRepository;
	@Autowired
	private PayoutTransferRepository payoutTransferRepository;
	@Autowired
	private TransactionTemplate transactionTemplate;

	@AfterEach
	void tearDown() {
		payoutTransferRepository.deleteAll();
		walletLedgerRepository.deleteAll();
		walletRepository.deleteAll();
		cashSellerRepository.deleteAll();
		cashMemberRepository.deleteAll();
	}

	@Test
	@DisplayName("같은 정산 ID로 10개 스레드가 동시에 지급 요청해도 대금·수수료 이동은 한 번만 일어난다")
	void concurrentSamePayoutId_transfersOnlyOnce() throws InterruptedException {
		createWallet(cashWalletProperties.holdingMemberId(), "홀딩");
		createWallet(cashWalletProperties.systemMemberId(), "시스템");
		createSellerWithMapping();

		List<AttemptResult> results = runConcurrently(10, i -> () ->
			cashFacade.payout(PAYOUT_ID, SELLER_APPLICATION_ID, PayoutStage.FINAL, 90_000, 5_500));

		long paidCount = results.stream().filter(result -> result.dto() != null
			&& result.dto().outcome() == CashPayoutResponseDto.Outcome.PAID).count();
		long alreadyPaidCount = results.stream().filter(result -> result.dto() != null
			&& result.dto().outcome() == CashPayoutResponseDto.Outcome.ALREADY_PAID).count();
		long errorCount = results.stream().filter(result -> result.error() != null).count();

		printAttemptSummary(results);

		assertThat(errorCount).as("예외 없이 전부 응답해야 한다").isZero();
		assertThat(paidCount).as("딱 한 번만 실제로 지급돼야 한다").isEqualTo(1);
		assertThat(alreadyPaidCount).as("나머지는 이미 지급됨으로 수렴해야 한다").isEqualTo(9);
		assertBalance(cashWalletProperties.holdingMemberId(), -95_500);
		assertBalance(SELLER_MEMBER_ID, 90_000);
		assertBalance(cashWalletProperties.systemMemberId(), 5_500);
		assertThat(walletLedgerRepository.findAll()).as("장부는 대금 2줄 + 수수료 2줄만 있어야 한다").hasSize(4);
		assertThat(payoutTransferRepository.count()).as("이동 기록도 대금·수수료 하나씩만 있어야 한다").isEqualTo(2);
	}

	private List<AttemptResult> runConcurrently(
		int threadCount, IntFunction<Callable<CashPayoutResponseDto>> taskFactory
	) throws InterruptedException {
		ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		CountDownLatch startSignal = new CountDownLatch(1);
		List<Future<CashPayoutResponseDto>> futures = new ArrayList<>();
		for (int i = 0; i < threadCount; i++) {
			Callable<CashPayoutResponseDto> task = taskFactory.apply(i);
			futures.add(executor.submit(() -> {
				startSignal.await();
				return task.call();
			}));
		}
		startSignal.countDown();

		List<AttemptResult> results = new ArrayList<>();
		for (Future<CashPayoutResponseDto> future : futures) {
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
				System.out.printf("PRO-256 #%d 예외: %s: %s%n",
					i, attempt.error().getClass().getName(), attempt.error().getMessage());
			} else {
				System.out.printf("PRO-256 #%d 결과: %s%n", i, attempt.dto().outcome());
			}
		}
	}

	private void assertBalance(long holderId, int expected) {
		assertThat(walletRepository.findById(holderId).orElseThrow().getBalance()).isEqualTo(expected);
	}

	private void createWallet(long memberId, String nickname) {
		transactionTemplate.executeWithoutResult(status -> {
			LocalDateTime now = LocalDateTime.of(2026, 10, 20, 10, 0);
			CashMember member = cashMemberRepository.save(new CashMember(memberId, now, now, nickname));
			walletRepository.save(new Wallet(member));
		});
	}

	// 판매자 신청서 ID(SELLER_APPLICATION_ID) → 회원(SELLER_MEMBER_ID) 매핑까지 함께 만든다
	private void createSellerWithMapping() {
		transactionTemplate.executeWithoutResult(status -> {
			LocalDateTime now = LocalDateTime.of(2026, 10, 20, 10, 0);
			CashMember member = cashMemberRepository.save(new CashMember(SELLER_MEMBER_ID, now, now, "판매자"));
			walletRepository.save(new Wallet(member));
			cashSellerRepository.save(new CashSeller(SELLER_APPLICATION_ID, member));
		});
	}

	private record AttemptResult(CashPayoutResponseDto dto, Throwable error) {
	}
}
