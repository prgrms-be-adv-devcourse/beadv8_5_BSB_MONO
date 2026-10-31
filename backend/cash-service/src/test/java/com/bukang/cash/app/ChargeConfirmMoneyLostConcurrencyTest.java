package com.bukang.cash.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
import com.bukang.cash.domain.wallet.LedgerRefType;
import com.bukang.cash.domain.wallet.LedgerType;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.dto.CashChargeResponseDto;
import com.bukang.cash.out.CashMemberRepository;
import com.bukang.cash.out.WalletChargeRepository;
import com.bukang.cash.out.WalletLedgerRepository;
import com.bukang.cash.out.WalletRepository;
import com.bukang.cash.out.pg.PaymentApprovalResult;
import com.bukang.cash.out.pg.PaymentClient;

// Toss 승인을 기다리는 동안 다른 트랜잭션이 같은 지갑을 먼저 바꿔 커밋해도,
// Toss 호출이 트랜잭션 밖에 있고 지갑 반영은 비관적 락으로 보호되므로 버전 충돌 없이 충전이 끝까지 성공해야 한다.
// (고치기 전에는 이 상황에서 ObjectOptimisticLockingFailureException으로 충전 전체가 롤백됐다 — money lost 재현)
@SpringBootTest
@ActiveProfiles("test")
class ChargeConfirmMoneyLostConcurrencyTest {
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
	@DisplayName("Toss 승인을 기다리는 동안 다른 트랜잭션이 지갑을 먼저 바꿔도, "
		+ "버전 충돌 없이 충전이 끝까지 성공하고 돈이 유실되지 않는다")
	void tossApprovedAndConcurrentWalletChangeDoesNotLoseCharge() throws InterruptedException, ExecutionException,
		TimeoutException {
		createCustomerWallet();
		CountDownLatch pgCalled = new CountDownLatch(1);
		CountDownLatch otherTxCommitted = new CountDownLatch(1);

		doAnswer(invocation -> {
			pgCalled.countDown();
			otherTxCommitted.await(5, TimeUnit.SECONDS);
			return new PaymentApprovalResult(10_000, "toss_key_1");
		}).when(paymentClient).approve(anyString(), anyString(), anyLong());

		ExecutorService executor = Executors.newSingleThreadExecutor();
		Callable<CashChargeResponseDto> chargeTask = () ->
			cashFacade.confirmWalletCharge(CUSTOMER_ID, "pay_1", "order_1", 10_000);
		Future<CashChargeResponseDto> future = executor.submit(chargeTask);

		assertThat(pgCalled.await(5, TimeUnit.SECONDS))
			.as("충전이 지갑을 먼저 잠깐 잠갔다 풀고 Toss 승인을 호출할 때까지 기다린다")
			.isTrue();

		// Toss 응답을 기다리는 동안, 다른 트랜잭션이 같은 지갑을 먼저 바꿔 커밋한다 (버전이 올라감)
		transactionTemplate.executeWithoutResult(status -> {
			Wallet wallet = walletRepository.findById(CUSTOMER_ID).orElseThrow();
			walletLedgerRepository.save(
				wallet.credit(1, LedgerType.충전__PG결제_토스페이먼츠, LedgerRefType.WalletCharge, 999));
			walletRepository.saveAndFlush(wallet);
		});
		otherTxCommitted.countDown();

		CashChargeResponseDto response = future.get(5, TimeUnit.SECONDS);
		executor.shutdown();

		assertThat(response.outcome()).isEqualTo(CashChargeResponseDto.Outcome.COMPLETED);
		verify(paymentClient).approve("pay_1", "order_1", 10_000);
		WalletCharge charge = walletChargeRepository.findByIdempotencyKey("order_1").orElseThrow();
		assertThat(charge.getStatus()).isEqualTo(ChargeStatus.COMPLETED);
		assertThat(walletRepository.findById(CUSTOMER_ID).orElseThrow().getBalance())
			.as("다른 트랜잭션이 올린 +1원에 충전 10,000원이 그대로 더해져야 한다 (유실 없음)")
			.isEqualTo(10_001);
	}

	private void createCustomerWallet() {
		transactionTemplate.executeWithoutResult(status -> {
			LocalDateTime now = LocalDateTime.of(2026, 10, 18, 10, 0);
			CashMember member = cashMemberRepository.save(new CashMember(CUSTOMER_ID, now, now, "러너" + CUSTOMER_ID));
			walletRepository.save(new Wallet(member));
		});
	}
}
