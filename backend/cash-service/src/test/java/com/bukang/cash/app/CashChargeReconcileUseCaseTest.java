package com.bukang.cash.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.cash.domain.charge.ChargeStatus;
import com.bukang.cash.domain.charge.WalletCharge;
import com.bukang.cash.domain.exception.PaymentLookupFailedException;
import com.bukang.cash.domain.member.CashMember;
import com.bukang.cash.domain.wallet.LedgerRefType;
import com.bukang.cash.domain.wallet.LedgerType;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.out.CashMemberRepository;
import com.bukang.cash.out.WalletChargeRepository;
import com.bukang.cash.out.WalletLedgerRepository;
import com.bukang.cash.out.WalletRepository;
import com.bukang.cash.out.pg.PaymentApprovalResult;
import com.bukang.cash.out.pg.PaymentClient;
import com.bukang.cash.out.pg.PaymentLookupResult;

// FakePaymentClient(mode=fake)는 항상 성공해서 조회 분기를 확인할 수 없으므로, PaymentClient를 Mockito 목으로 바꾼다
@SpringBootTest
@ActiveProfiles("test")
class CashChargeReconcileUseCaseTest {
	private static final long CUSTOMER_ID = 3;
	// application-test.yaml의 cash.charge.reconcile.stale-in-progress-minutes와 맞춘다
	private static final long STALE_MINUTES = 5;

	@Autowired
	private CashChargeReconcileUseCase cashChargeReconcileUseCase;
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
	private JdbcTemplate jdbcTemplate;

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
	@DisplayName("UNCERTAIN 건을 재조회해서 PG가 승인했다고 하면 COMPLETED로 맞추고 지갑에 반영한다")
	void uncertainChargeResolvedToCompletedWhenPgNowConfirms() {
		createCustomerWallet(0);
		createCharge("order_1", ChargeStatus.UNCERTAIN, null);
		given(paymentClient.lookupByOrderId("order_1"))
			.willReturn(PaymentLookupResult.completed(new PaymentApprovalResult(10_000, "toss_key_1")));

		cashChargeReconcileUseCase.reconcile(LocalDateTime.now());

		WalletCharge charge = walletChargeRepository.findByIdempotencyKey("order_1").orElseThrow();
		assertThat(charge.getStatus()).isEqualTo(ChargeStatus.COMPLETED);
		assertThat(charge.getPgPaymentKey()).isEqualTo("toss_key_1");
		assertBalance(CUSTOMER_ID, 10_000);
		assertThat(walletLedgerRepository.findAll()).hasSize(1);
	}

	@Test
	@DisplayName("UNCERTAIN 건을 재조회해서 PG가 승인 안 됐다고 확인되면 FAILED로 맞춘다")
	void uncertainChargeResolvedToFailedWhenPgConfirmsNotCompleted() {
		createCustomerWallet(0);
		createCharge("order_1", ChargeStatus.UNCERTAIN, null);
		given(paymentClient.lookupByOrderId("order_1")).willReturn(PaymentLookupResult.notCompleted());

		cashChargeReconcileUseCase.reconcile(LocalDateTime.now());

		WalletCharge charge = walletChargeRepository.findByIdempotencyKey("order_1").orElseThrow();
		assertThat(charge.getStatus()).isEqualTo(ChargeStatus.FAILED);
		assertBalance(CUSTOMER_ID, 0);
	}

	@Test
	@DisplayName("UNCERTAIN 건을 재조회했는데 PG가 아직 처리 중이면 FAILED로 단정하지 않고 다음 실행으로 미룬다")
	void uncertainChargeStaysUncertainWhenPgStillPending() {
		createCustomerWallet(0);
		createCharge("order_1", ChargeStatus.UNCERTAIN, null);
		given(paymentClient.lookupByOrderId("order_1")).willReturn(PaymentLookupResult.pending());

		cashChargeReconcileUseCase.reconcile(LocalDateTime.now());

		WalletCharge charge = walletChargeRepository.findByIdempotencyKey("order_1").orElseThrow();
		assertThat(charge.getStatus()).isEqualTo(ChargeStatus.UNCERTAIN);
		assertBalance(CUSTOMER_ID, 0);
	}

	@Test
	@DisplayName("UNCERTAIN 건을 재조회했는데 그마저 또 실패하면 상태를 바꾸지 않고 다음 실행으로 미룬다")
	void uncertainChargeStaysUncertainWhenPgLookupFailsAgain() {
		createCustomerWallet(0);
		createCharge("order_1", ChargeStatus.UNCERTAIN, null);
		willThrow(new PaymentLookupFailedException("조회 실패", new RuntimeException("timeout")))
			.given(paymentClient).lookupByOrderId("order_1");

		cashChargeReconcileUseCase.reconcile(LocalDateTime.now());

		WalletCharge charge = walletChargeRepository.findByIdempotencyKey("order_1").orElseThrow();
		assertThat(charge.getStatus()).isEqualTo(ChargeStatus.UNCERTAIN);
		assertBalance(CUSTOMER_ID, 0);
	}

	@Test
	@DisplayName("PG 조회가 3번 연달아 실패하면 PG가 내려간 것으로 보고 남은 건은 조회하지 않고 이번 실행을 멈춘다")
	void stopsRunAfterConsecutiveLookupFailures() {
		createCustomerWallet(0);
		for (int i = 1; i <= 5; i++) {
			createCharge("order_" + i, ChargeStatus.UNCERTAIN, null);
		}
		willThrow(new PaymentLookupFailedException("조회 실패", new RuntimeException("timeout")))
			.given(paymentClient).lookupByOrderId(anyString());

		cashChargeReconcileUseCase.reconcile(LocalDateTime.now());

		verify(paymentClient, times(3)).lookupByOrderId(anyString());
	}

	@Test
	@DisplayName("PG가 처리 중이라고 답한 건은 조회 실패로 세지 않아, 실행을 멈추지 않고 다음 건까지 맞춘다")
	void pendingAnswersDoNotStopRun() {
		createCustomerWallet(0);
		LocalDateTime now = LocalDateTime.now();
		createCharge("order_1", ChargeStatus.UNCERTAIN, now.minusMinutes(4));
		createCharge("order_2", ChargeStatus.UNCERTAIN, now.minusMinutes(3));
		createCharge("order_3", ChargeStatus.UNCERTAIN, now.minusMinutes(2));
		createCharge("order_4", ChargeStatus.UNCERTAIN, now.minusMinutes(1));
		given(paymentClient.lookupByOrderId(anyString())).willReturn(PaymentLookupResult.pending());
		given(paymentClient.lookupByOrderId("order_4"))
			.willReturn(PaymentLookupResult.completed(new PaymentApprovalResult(10_000, "toss_key_4")));

		cashChargeReconcileUseCase.reconcile(now);

		assertThat(walletChargeRepository.findByIdempotencyKey("order_4").orElseThrow().getStatus())
			.isEqualTo(ChargeStatus.COMPLETED);
		assertBalance(CUSTOMER_ID, 10_000);
	}

	@Test
	@DisplayName("IN_PROGRESS로 멈춘 지 오래된 건은 대상에 넣어 재조회로 맞춘다")
	void staleInProgressChargeIsPickedUpAndResolved() {
		createCustomerWallet(0);
		createCharge("order_1", ChargeStatus.IN_PROGRESS, LocalDateTime.now().minusMinutes(STALE_MINUTES + 5));
		given(paymentClient.lookupByOrderId("order_1"))
			.willReturn(PaymentLookupResult.completed(new PaymentApprovalResult(10_000, "toss_key_1")));

		cashChargeReconcileUseCase.reconcile(LocalDateTime.now());

		WalletCharge charge = walletChargeRepository.findByIdempotencyKey("order_1").orElseThrow();
		assertThat(charge.getStatus()).isEqualTo(ChargeStatus.COMPLETED);
		assertBalance(CUSTOMER_ID, 10_000);
	}

	@Test
	@DisplayName("IN_PROGRESS로 멈춘 지 얼마 안 된 건(정상 처리 중일 수 있음)은 건드리지 않는다")
	void recentInProgressChargeIsNotTouched() {
		createCustomerWallet(0);
		createCharge("order_1", ChargeStatus.IN_PROGRESS, null);

		cashChargeReconcileUseCase.reconcile(LocalDateTime.now());

		WalletCharge charge = walletChargeRepository.findByIdempotencyKey("order_1").orElseThrow();
		assertThat(charge.getStatus()).isEqualTo(ChargeStatus.IN_PROGRESS);
		assertBalance(CUSTOMER_ID, 0);
		verify(paymentClient, never()).lookupByOrderId(anyString());
	}

	// modifyDateOverride가 있으면 저장 뒤 DB 칸을 직접 고쳐 @LastModifiedDate가 "방금"으로 되돌리지 못하게 한다
	private void createCharge(String orderId, ChargeStatus status, LocalDateTime modifyDateOverride) {
		transactionTemplate.executeWithoutResult(txStatus -> {
			Wallet wallet = walletRepository.findById(CUSTOMER_ID).orElseThrow();
			WalletCharge charge = walletChargeRepository.save(new WalletCharge(wallet, 10_000, "TOSS", orderId));
			ReflectionTestUtils.setField(charge, "status", status);
			walletChargeRepository.saveAndFlush(charge);
			if (modifyDateOverride != null) {
				jdbcTemplate.update(
					"UPDATE cash_wallet_charge SET modify_date = ? WHERE idempotency_key = ?",
					Timestamp.valueOf(modifyDateOverride), orderId);
			}
		});
	}

	private Wallet createCustomerWallet(long initialBalance) {
		return transactionTemplate.execute(status -> {
			LocalDateTime now = LocalDateTime.of(2026, 10, 15, 10, 0);
			CashMember member = cashMemberRepository.save(new CashMember(CUSTOMER_ID, now, now, "러너" + CUSTOMER_ID));
			Wallet wallet = walletRepository.save(new Wallet(member));
			if (initialBalance != 0) {
				walletLedgerRepository.save(wallet.credit(
					initialBalance, LedgerType.충전__PG결제_토스페이먼츠, LedgerRefType.WalletCharge, CUSTOMER_ID));
			}
			return wallet;
		});
	}

	private void assertBalance(long holderId, long expected) {
		assertThat(walletRepository.findById(holderId).orElseThrow().getBalance()).isEqualTo(expected);
	}
}
