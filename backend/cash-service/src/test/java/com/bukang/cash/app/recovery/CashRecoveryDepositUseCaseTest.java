package com.bukang.cash.app.recovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;-recovery-deposit-reconcile
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.cash.app.CashFacade;
import com.bukang.cash.config.CashWalletProperties;
import com.bukang.cash.domain.exception.charge.PaymentLookupFailedException;
import com.bukang.cash.domain.member.CashMember;
import com.bukang.cash.domain.recovery.RecoveryDeposit;
import com.bukang.cash.domain.recovery.RecoveryDepositStatus;
import com.bukang.cash.domain.wallet.LedgerRefType;
import com.bukang.cash.domain.wallet.LedgerType;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.domain.wallet.WalletLedger;
import com.bukang.cash.out.member.CashMemberRepository;
import com.bukang.cash.out.pg.PaymentApprovalResult;
import com.bukang.cash.out.pg.PaymentClient;
import com.bukang.cash.out.pg.PaymentLookupResult;
import com.bukang.cash.out.recovery.RecoveryDepositRepository;
import com.bukang.cash.out.wallet.WalletLedgerRepository;
import com.bukang.cash.out.wallet.WalletRepository;

// 토스 입금 알림을 받아 회수 입금을 홀딩 지갑에 기록하는 유스케이스 테스트. PG 조회는 목으로 결과를 정한다
@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class CashRecoveryDepositUseCaseTest {
	private static final String CLAIM_CODE = "RC-20261214-0A3F";
	private static final String SECRET = "ps_secret_1";
	private static final LocalDateTime DEPOSITED_AT = LocalDateTime.of(2026, 12, 20, 14, 2);
	// 회수 입금의 입금 기한은 2027-03-14 10:00이다
	private static final LocalDateTime BEFORE_DUE = LocalDateTime.of(2026, 12, 21, 9, 0);
	private static final LocalDateTime AFTER_DUE = LocalDateTime.of(2027, 3, 14, 10, 1);

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
	private RecoveryDepositRepository recoveryDepositRepository;
	@Autowired
	private TransactionTemplate transactionTemplate;
	@Autowired
	private CashRecoveryDepositUseCase cashRecoveryDepositUseCase;
	@MockitoBean
	private PaymentClient paymentClient;

	private RecoveryDeposit deposit;

	@BeforeEach
	void setUp() {
		transactionTemplate.executeWithoutResult(status -> {
			LocalDateTime now = LocalDateTime.of(2026, 12, 1, 10, 0);
			CashMember holding = cashMemberRepository.save(
				new CashMember(cashWalletProperties.holdingMemberId(), now, now, "홀딩"));
			walletRepository.save(new Wallet(holding));
		});
		deposit = recoveryDepositRepository.save(new RecoveryDeposit(
			CLAIM_CODE, 400_000, "국민은행", "X6000123456789", LocalDateTime.of(2027, 3, 14, 10, 0), SECRET));
	}

	@AfterEach
	void tearDown() {
		walletLedgerRepository.deleteAll();
		walletRepository.deleteAll();
		cashMemberRepository.deleteAll();
		recoveryDepositRepository.deleteAll();
	}

	@Test
	@DisplayName("입금 알림이 오고 PG 조회가 입금 완료·같은 금액이면 홀딩 지갑에 회수 금액 한 줄을 남기고 입금 완료로 바꾼다")
	void recordsDepositToHoldingWallet() {
		given(paymentClient.lookupByOrderId(CLAIM_CODE)).willReturn(
			PaymentLookupResult.completed(new PaymentApprovalResult(400_000, "pay_key_1", DEPOSITED_AT)));

		cashFacade.receiveRecoveryDeposit(CLAIM_CODE, SECRET, "DONE");

		assertThat(walletRepository.findById(cashWalletProperties.holdingMemberId()).orElseThrow().getBalance())
			.isEqualTo(400_000);
		List<WalletLedger> ledgers = walletLedgerRepository.findAll();
		assertThat(ledgers).singleElement().satisfies(ledger -> {
			assertThat(ledger.getLedgerType()).isEqualTo(LedgerType.회수입금__가상계좌_토스페이먼츠);
			assertThat(ledger.getAmount()).isEqualTo(400_000);
			assertThat(ledger.getRefTypeCode()).isEqualTo(LedgerRefType.RecoveryDeposit);
			assertThat(ledger.getRefId()).isEqualTo(deposit.getId());
		});
		RecoveryDeposit saved = recoveryDepositRepository.findById(deposit.getId()).orElseThrow();
		assertThat(saved.getStatus()).isEqualTo(RecoveryDepositStatus.DEPOSITED);
		assertThat(saved.getDepositedAt()).isEqualTo(DEPOSITED_AT);
	}

	@Test
	@DisplayName("알림의 비밀값이 발급 때 받은 값과 다르면 PG를 조회하지 않고, 장부도 상태도 그대로 둔다")
	void ignoresCallbackWithWrongSecret() {
		given(paymentClient.lookupByOrderId(CLAIM_CODE)).willReturn(
			PaymentLookupResult.completed(new PaymentApprovalResult(400_000, "pay_key_1", DEPOSITED_AT)));

		cashFacade.receiveRecoveryDeposit(CLAIM_CODE, "forged_secret", "DONE");

		verify(paymentClient, never()).lookupByOrderId(anyString());
		assertNothingRecorded();
	}

	@Test
	@DisplayName("회수 입금 기록에 없는 주문번호의 알림이면 오류 없이 끝내고, PG 조회도 기록도 하지 않는다")
	void ignoresCallbackForUnknownOrder() {
		assertThatCode(() -> cashFacade.receiveRecoveryDeposit("RC-20261214-FFFF", SECRET, "DONE"))
			.doesNotThrowAnyException();

		verify(paymentClient, never()).lookupByOrderId(anyString());
		assertNothingRecorded();
	}

	@Test
	@DisplayName("PG 조회 결과가 아직 입금 완료가 아니면 기록하지 않고 입금 대기로 둔다")
	void ignoresWhenPgSaysNotDeposited() {
		given(paymentClient.lookupByOrderId(CLAIM_CODE)).willReturn(PaymentLookupResult.pending());
		cashFacade.receiveRecoveryDeposit(CLAIM_CODE, SECRET, "DONE");
		assertNothingRecorded();

		given(paymentClient.lookupByOrderId(CLAIM_CODE)).willReturn(PaymentLookupResult.notCompleted());
		cashFacade.receiveRecoveryDeposit(CLAIM_CODE, SECRET, "DONE");
		assertNothingRecorded();
	}

	@Test
	@DisplayName("PG 조회의 입금 금액이 청구 금액과 다르면 기록하지 않고 입금 대기로 둔다 (사람이 맞춘다)")
	void ignoresWhenDepositedAmountDiffers() {
		given(paymentClient.lookupByOrderId(CLAIM_CODE)).willReturn(
			PaymentLookupResult.completed(new PaymentApprovalResult(390_000, "pay_key_1", DEPOSITED_AT)));

		cashFacade.receiveRecoveryDeposit(CLAIM_CODE, SECRET, "DONE");

		assertNothingRecorded();
	}

	@Test
	@DisplayName("이미 입금 완료로 기록한 청구에 같은 알림이 다시 오면 PG를 조회하지 않고 장부도 더 남기지 않는다")
	void ignoresRepeatedCallbackAfterDeposit() {
		given(paymentClient.lookupByOrderId(CLAIM_CODE)).willReturn(
			PaymentLookupResult.completed(new PaymentApprovalResult(400_000, "pay_key_1", DEPOSITED_AT)));
		cashFacade.receiveRecoveryDeposit(CLAIM_CODE, SECRET, "DONE");

		cashFacade.receiveRecoveryDeposit(CLAIM_CODE, SECRET, "DONE");

		verify(paymentClient, times(1)).lookupByOrderId(CLAIM_CODE);
		assertRecordedOnce();
	}

	@Test
	@DisplayName("입금 완료로 기록한 청구에 입금 오류 알림(DONE이 아닌 상태)이 오면 장부는 그대로 두고, 사람이 맞추도록 에러 로그를 남긴다")
	void logsErrorWhenDepositRevertedAfterRecording(CapturedOutput output) {
		given(paymentClient.lookupByOrderId(CLAIM_CODE)).willReturn(
			PaymentLookupResult.completed(new PaymentApprovalResult(400_000, "pay_key_1", DEPOSITED_AT)));
		cashFacade.receiveRecoveryDeposit(CLAIM_CODE, SECRET, "DONE");

		cashFacade.receiveRecoveryDeposit(CLAIM_CODE, SECRET, "WAITING_FOR_DEPOSIT");

		verify(paymentClient, times(1)).lookupByOrderId(CLAIM_CODE);
		assertRecordedOnce();
		assertThat(output).contains("입금 오류", CLAIM_CODE);
	}

	@Test
	@DisplayName("같은 알림이 10개 스레드에서 동시에 와도 오류 없이 홀딩 지갑에는 한 번만 기록한다")
	void recordsOnceUnderConcurrentCallbacks() throws Exception {
		given(paymentClient.lookupByOrderId(CLAIM_CODE)).willReturn(
			PaymentLookupResult.completed(new PaymentApprovalResult(400_000, "pay_key_1", DEPOSITED_AT)));
		int threadCount = 10;
		ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		CountDownLatch startSignal = new CountDownLatch(1);
		List<Future<?>> futures = new ArrayList<>();
		for (int i = 0; i < threadCount; i++) {
			futures.add(executor.submit(() -> {
				startSignal.await();
				cashFacade.receiveRecoveryDeposit(CLAIM_CODE, SECRET, "DONE");
				return null;
			}));
		}
		startSignal.countDown();
		for (Future<?> future : futures) {
			future.get();
		}
		executor.shutdown();

		assertRecordedOnce();
	}

	@Test
	@DisplayName("입금 알림이 오지 않아도 재확인이 입금 대기 건을 PG에 다시 조회해 입금 완료면 홀딩 지갑에 기록한다")
	void reconcileRecordsDepositWithoutCallback() {
		given(paymentClient.lookupByOrderId(CLAIM_CODE)).willReturn(
			PaymentLookupResult.completed(new PaymentApprovalResult(400_000, "pay_key_1", DEPOSITED_AT)));

		cashRecoveryDepositUseCase.reconcile(BEFORE_DUE);

		assertRecordedOnce();
	}

	@Test
	@DisplayName("재확인 중 한 건의 PG 조회가 실패해도 다른 입금 대기 건은 계속 확인한다")
	void reconcileContinuesAfterLookupFailure() {
		RecoveryDeposit failing = recoveryDepositRepository.save(new RecoveryDeposit(
			"RC-20261213-0001", 100_000, "국민은행", "X6000000000001", LocalDateTime.of(2027, 3, 13, 10, 0), "s"));
		given(paymentClient.lookupByOrderId(failing.getClaimCode()))
			.willThrow(new PaymentLookupFailedException("timeout", null));
		given(paymentClient.lookupByOrderId(CLAIM_CODE)).willReturn(
			PaymentLookupResult.completed(new PaymentApprovalResult(400_000, "pay_key_1", DEPOSITED_AT)));

		cashRecoveryDepositUseCase.reconcile(BEFORE_DUE);

		assertRecordedOnce();
		assertThat(recoveryDepositRepository.findById(failing.getId()).orElseThrow().getStatus())
			.isEqualTo(RecoveryDepositStatus.WAITING);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("overdueLookups")
	@DisplayName("입금 기한이 지난 뒤 재확인은 PG가 승인 안 됨·입금 대기라고 할 때만 기한 지남으로 바꾸고, 기록은 하지 않는다")
	void reconcileAfterDueByPgStatus(String pgAnswer, PaymentLookupResult lookup, RecoveryDepositStatus expected) {
		given(paymentClient.lookupByOrderId(CLAIM_CODE)).willReturn(lookup);

		cashRecoveryDepositUseCase.reconcile(AFTER_DUE);

		assertThat(recoveryDepositRepository.findById(deposit.getId()).orElseThrow().getStatus())
			.isEqualTo(expected);
		assertThat(walletLedgerRepository.count()).isZero();
	}

	static Stream<Arguments> overdueLookups() {
		return Stream.of(
			Arguments.of("승인 안 됨이면 기한 지남", PaymentLookupResult.notCompleted(), RecoveryDepositStatus.EXPIRED),
			Arguments.of("입금 대기면 기한 지남", PaymentLookupResult.waitingForDeposit(), RecoveryDepositStatus.EXPIRED),
			Arguments.of("일부 취소·모르는 값처럼 아직 정해지지 않았으면 입금 대기 그대로",
				PaymentLookupResult.pending(), RecoveryDepositStatus.WAITING));
	}

	@Test
	@DisplayName("입금 기한이 지났어도 PG가 입금 완료라고 하면(기한 직전 입금, 알림 빠짐) 기한 지남이 아니라 입금으로 기록한다")
	void reconcileRecordsDepositMadeBeforeDueEvenAfterDue() {
		given(paymentClient.lookupByOrderId(CLAIM_CODE)).willReturn(
			PaymentLookupResult.completed(new PaymentApprovalResult(400_000, "pay_key_1", DEPOSITED_AT)));

		cashRecoveryDepositUseCase.reconcile(AFTER_DUE);

		assertRecordedOnce();
	}

	@Test
	@DisplayName("입금 기한이 지났어도 PG 조회가 실패하면 입금됐는지 모르므로 기한 지남으로 바꾸지 않는다")
	void reconcileKeepsWaitingWhenLookupFailsAfterDue() {
		given(paymentClient.lookupByOrderId(CLAIM_CODE)).willThrow(new PaymentLookupFailedException("timeout", null));

		cashRecoveryDepositUseCase.reconcile(AFTER_DUE);

		assertNothingRecorded();
	}

	@Test
	@DisplayName("입금 기한 전이면 PG가 입금 전이라고 해도 입금 대기로 둔다")
	void reconcileKeepsWaitingBeforeDue() {
		given(paymentClient.lookupByOrderId(CLAIM_CODE)).willReturn(PaymentLookupResult.notCompleted());

		cashRecoveryDepositUseCase.reconcile(BEFORE_DUE);

		assertNothingRecorded();
	}

	private void assertRecordedOnce() {
		assertThat(walletRepository.findById(cashWalletProperties.holdingMemberId()).orElseThrow().getBalance())
			.isEqualTo(400_000);
		assertThat(walletLedgerRepository.count()).isEqualTo(1);
		assertThat(recoveryDepositRepository.findById(deposit.getId()).orElseThrow().getStatus())
			.isEqualTo(RecoveryDepositStatus.DEPOSITED);
	}

	private void assertNothingRecorded() {
		assertThat(walletRepository.findById(cashWalletProperties.holdingMemberId()).orElseThrow().getBalance())
			.isZero();
		assertThat(walletLedgerRepository.count()).isZero();
		assertThat(recoveryDepositRepository.findById(deposit.getId()).orElseThrow().getStatus())
			.isEqualTo(RecoveryDepositStatus.WAITING);
	}
}
