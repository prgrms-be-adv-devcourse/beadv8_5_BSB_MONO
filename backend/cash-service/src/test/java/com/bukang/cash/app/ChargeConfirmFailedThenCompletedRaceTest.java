package com.bukang.cash.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.cash.domain.charge.ChargeStatus;
import com.bukang.cash.domain.charge.WalletCharge;
import com.bukang.cash.domain.member.CashMember;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.dto.CashChargeResponseDto;
import com.bukang.cash.out.CashMemberRepository;
import com.bukang.cash.out.WalletChargeRepository;
import com.bukang.cash.out.WalletLedgerRepository;
import com.bukang.cash.out.WalletRepository;
import com.bukang.cash.out.pg.PaymentApprovalResult;
import com.bukang.cash.out.pg.PaymentClient;

// 이전: 같은 orderId로 동시에 두 confirm 요청이 오면(새로고침·중복 클릭) 둘 다 Toss까지 가서,
// 뒤처진 요청이 먼저 거절당해 FAILED를 저장해버린 뒤 실제로 승인된 다른 요청이 그 FAILED 기록을 그대로
// 믿고 돌려주면 "돈은 나갔는데 FAILED로 기록되는" money-lost가 될 수 있었다 (PR 리뷰 지적).
// 지금: IN_PROGRESS 선점 덕분에 뒤에 온 B는 애초에 Toss를 부르지 않고 먼저 온 A가 끝나길
// 기다렸다가 A의 실제 결과를 그대로 받는다 — money-lost 시나리오 자체가 더는 일어나지 않는다.
@SpringBootTest
@ActiveProfiles("test")
class ChargeConfirmFailedThenCompletedRaceTest {
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
	@DisplayName("같은 orderId로 A가 먼저 선점(IN_PROGRESS)한 뒤 B가 뒤따라오면, "
		+ "B는 Toss를 부르지 않고 기다렸다가 A의 실제 결과(COMPLETED)를 그대로 받는다")
	void laterRequestWaitsInsteadOfCallingTossAgain() throws Exception {
		createCustomerWallet();
		CountDownLatch aReachedToss = new CountDownLatch(1);
		CountDownLatch bSubmitted = new CountDownLatch(1);

		// A: Toss 호출에 들어서면 신호를 보내고, B가 제출될 때까지 기다렸다가 승인을 돌려준다
		given(paymentClient.approve(eq("pay_a"), eq("order_1"), anyLong())).willAnswer(invocation -> {
			aReachedToss.countDown();
			bSubmitted.await(5, TimeUnit.SECONDS);
			return new PaymentApprovalResult(10_000, "toss_key_a");
		});

		ExecutorService executor = Executors.newFixedThreadPool(2);
		Future<CashChargeResponseDto> aFuture = executor.submit(() ->
			cashFacade.confirmWalletCharge(CUSTOMER_ID, "pay_a", "order_1", 10_000));

		assertThat(aReachedToss.await(5, TimeUnit.SECONDS))
			.as("A가 Toss 호출 지점까지 가야 B를 시작한다")
			.isTrue();

		Future<CashChargeResponseDto> bFuture = executor.submit(() ->
			cashFacade.confirmWalletCharge(CUSTOMER_ID, "pay_b", "order_1", 10_000));
		bSubmitted.countDown();

		CashChargeResponseDto aResponse = aFuture.get(5, TimeUnit.SECONDS);
		CashChargeResponseDto bResponse = bFuture.get(5, TimeUnit.SECONDS);
		executor.shutdown();

		assertThat(aResponse.outcome()).isEqualTo(CashChargeResponseDto.Outcome.COMPLETED);
		assertThat(bResponse.outcome())
			.as("B는 A를 기다렸다가 같은 결과(COMPLETED)를 받아야 한다")
			.isEqualTo(CashChargeResponseDto.Outcome.COMPLETED);
		verify(paymentClient, never()).approve(eq("pay_b"), eq("order_1"), anyLong());
		assertBalance(CUSTOMER_ID, 10_000);
		WalletCharge charge = walletChargeRepository.findByIdempotencyKey("order_1").orElseThrow();
		assertThat(charge.getStatus()).isEqualTo(ChargeStatus.COMPLETED);
		assertThat(charge.getPgPaymentKey()).isEqualTo("toss_key_a");
		assertThat(walletChargeRepository.count()).as("행은 하나만 있어야 한다(재사용, 새로 안 만듦)").isEqualTo(1);
		assertThat(walletLedgerRepository.findAll()).as("적립 장부도 한 줄만 남아야 한다").hasSize(1);
	}

	// A/B를 둘 다 실제로 돌려서 경쟁시키는 방식은 FAILED에서는 쓸 수 없다 — FAILED는 재선점이
	// 가능한 상태라, B의 claim 시도가 A의 FAILED 커밋보다 늦으면 "기다림"이 아니라 "새 재시도"가
	// 되어버린다(정상 동작, alreadyFailedRetriesPgInsteadOfShortCircuiting이 그 경우를 검증함).
	// 그래서 stuckInProgressEventuallyThrows처럼 상태를 직접 선점시켜 두고, 기다리는 동안 그 상태를
	// 바꿔 주는 방식으로 "기다리던 쪽이 FAILED를 그대로 받는지"만 통제된 타이밍으로 확인한다.
	@Test
	@DisplayName("IN_PROGRESS로 선점된 채 기다리던 중 선점한 쪽이 FAILED로 끝나면, "
		+ "기다리던 요청도 Toss를 부르지 않고 같은 FAILED 결과를 그대로 받는다")
	void waitingRequestReceivesFailedResultOnceResolved() throws Exception {
		createCustomerWallet();
		Wallet wallet = walletRepository.findById(CUSTOMER_ID).orElseThrow();
		WalletCharge charge = walletChargeRepository.save(new WalletCharge(wallet, 10_000, "TOSS", "order_1"));
		charge.markInProgress();
		walletChargeRepository.saveAndFlush(charge);

		ExecutorService executor = Executors.newSingleThreadExecutor();
		Future<CashChargeResponseDto> waitingFuture = executor.submit(() ->
			cashFacade.confirmWalletCharge(CUSTOMER_ID, "pay_b", "order_1", 10_000));

		// 기다리는 쪽이 몇 번은 폴링하도록(100ms 간격) 짧게 둔 뒤, 선점한 쪽이 끝난 것처럼 FAILED로 바꾼다
		Thread.sleep(250);
		transactionTemplate.executeWithoutResult(status ->
			walletChargeRepository.findByIdempotencyKey("order_1").orElseThrow().markFailed());

		CashChargeResponseDto response = waitingFuture.get(5, TimeUnit.SECONDS);
		executor.shutdown();

		assertThat(response.outcome())
			.as("기다리던 요청도 선점한 쪽과 같은 결과(FAILED)를 받아야 한다")
			.isEqualTo(CashChargeResponseDto.Outcome.FAILED);
		verify(paymentClient, never()).approve(anyString(), anyString(), anyLong());
		assertBalance(CUSTOMER_ID, 0);
		WalletCharge persisted = walletChargeRepository.findByIdempotencyKey("order_1").orElseThrow();
		assertThat(persisted.getStatus()).isEqualTo(ChargeStatus.FAILED);
		assertThat(walletChargeRepository.count()).as("행은 하나만 있어야 한다").isEqualTo(1);
	}

	private void createCustomerWallet() {
		transactionTemplate.executeWithoutResult(status -> {
			LocalDateTime now = LocalDateTime.of(2026, 10, 19, 10, 0);
			CashMember member = cashMemberRepository.save(new CashMember(CUSTOMER_ID, now, now, "러너" + CUSTOMER_ID));
			walletRepository.save(new Wallet(member));
		});
	}

	private void assertBalance(long holderId, int expected) {
		assertThat(walletRepository.findById(holderId).orElseThrow().getBalance()).isEqualTo(expected);
	}
}
