package com.bukang.cash.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.cash.domain.charge.WalletCharge;
import com.bukang.cash.domain.exception.PaymentCancelFailedException;
import com.bukang.cash.domain.exception.WithdrawalAccountRequiredException;
import com.bukang.cash.domain.exception.WithdrawalInProgressException;
import com.bukang.cash.domain.member.CashMember;
import com.bukang.cash.domain.wallet.LedgerType;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.domain.wallet.WalletLedger;
import com.bukang.cash.domain.withdrawal.WalletWithdrawal;
import com.bukang.cash.domain.withdrawal.WalletWithdrawalItem;
import com.bukang.cash.domain.withdrawal.WithdrawalItemStatus;
import com.bukang.cash.domain.withdrawal.WithdrawalStatus;
import com.bukang.cash.dto.CashWithdrawalResponseDto;
import com.bukang.cash.out.CashMemberRepository;
import com.bukang.cash.out.WalletChargeRepository;
import com.bukang.cash.out.WalletLedgerRepository;
import com.bukang.cash.out.WalletRepository;
import com.bukang.cash.out.WalletWithdrawalItemRepository;
import com.bukang.cash.out.WalletWithdrawalRepository;
import com.bukang.cash.out.pg.PaymentCancelResult;
import com.bukang.cash.out.pg.PaymentClient;

// 정책 5장 2차 1 「PG 결제취소와 기한」대로 환급을 충전 건별 PG 결제취소와 계좌 송금으로 나누는 흐름.
// 토스는 Mockito 목으로 바꿔 결과를 직접 정한다. 지갑 잔액은 ReflectionTestUtils로 넣는다
@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class CashWithdrawalByChargeTest {
	private static final long CUSTOMER_ID = 3;
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 10, 0);

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
	private WalletWithdrawalRepository walletWithdrawalRepository;
	@Autowired
	private TransactionTemplate transactionTemplate;
	@Autowired
	private PlatformTransactionManager transactionManager;
	@Autowired
	private CashWithdrawalReconcileUseCase cashWithdrawalReconcileUseCase;
	@Autowired
	private WalletWithdrawalItemRepository walletWithdrawalItemRepository;

	@MockitoBean
	private PaymentClient paymentClient;

	// 기본값: 토스는 요청한 금액 그대로 취소에 성공한다. 거절·결과 모름은 각 테스트가 따로 정한다
	@BeforeEach
	void pgCancelSucceedsByDefault() {
		given(paymentClient.cancel(anyString(), anyString(), anyLong(), anyString()))
			.willAnswer(invocation -> new PaymentCancelResult(invocation.getArgument(2), "txn_default"));
	}

	@AfterEach
	void tearDown() {
		walletWithdrawalItemRepository.deleteAll();
		walletWithdrawalRepository.deleteAll();
		walletLedgerRepository.deleteAll();
		walletChargeRepository.deleteAll();
		walletRepository.deleteAll();
		cashMemberRepository.deleteAll();
	}

	@Test
	@DisplayName("기한 안 충전만 있으면 계좌 정보 없이 신청해도 받아 주고 예치금이 0원이 된다")
	void cardOnlyRefundNeedsNoBankAccount() {
		Wallet wallet = createWallet(30_000);
		completedCharge(wallet, 30_000, "charge_1", LocalDateTime.of(2026, 10, 10, 9, 0));

		cashFacade.requestWithdrawal(CUSTOMER_ID, null, null, null, "withdrawal_1", NOW);

		assertThat(walletRepository.findById(CUSTOMER_ID).orElseThrow().getBalance()).isZero();
	}

	@Test
	@DisplayName("기한 안 충전 30,000원을 환급하면 그 충전의 결제 키로 30,000원 PG 결제취소를 요청한다")
	void requestsPgCancelForCardPart() {
		Wallet wallet = createWallet(30_000);
		completedCharge(wallet, 30_000, "charge_1", LocalDateTime.of(2026, 10, 10, 9, 0));

		cashFacade.requestWithdrawal(CUSTOMER_ID, null, null, null, "withdrawal_1", NOW);

		verify(paymentClient).cancel(eq("toss_key_charge_1"), anyString(), eq(30_000L), anyString());
	}

	@Test
	@DisplayName("카드 몫은 충전 건마다 출금__PG결제취소_토스페이먼츠 원장 줄로 남고, 계좌 송금 줄은 남지 않는다")
	void cardPartsLeaveOneLedgerLinePerCharge() {
		Wallet wallet = createWallet(30_000);
		completedCharge(wallet, 10_000, "charge_1", LocalDateTime.of(2026, 10, 10, 9, 0));
		completedCharge(wallet, 20_000, "charge_2", LocalDateTime.of(2026, 10, 13, 9, 0));

		cashFacade.requestWithdrawal(CUSTOMER_ID, null, null, null, "withdrawal_1", NOW);

		assertThat(walletLedgerRepository.findAll())
			.extracting(WalletLedger::getLedgerType, WalletLedger::getAmount)
			.containsExactlyInAnyOrder(
				tuple(LedgerType.출금__PG결제취소_토스페이먼츠, -20_000L),
				tuple(LedgerType.출금__PG결제취소_토스페이먼츠, -10_000L));
	}

	@Test
	@DisplayName("충전 30,000원과 정산 대금 100,000원이 섞인 잔액은 카드 몫 30,000원과 계좌 몫 100,000원으로 나뉜다")
	void payoutBalanceGoesToAccount() {
		Wallet wallet = createWallet(130_000);
		completedCharge(wallet, 30_000, "charge_1", LocalDateTime.of(2026, 10, 10, 9, 0));

		cashFacade.requestWithdrawal(CUSTOMER_ID, "004", "110123456789", "김러너", "withdrawal_1", NOW);

		verify(paymentClient).cancel(eq("toss_key_charge_1"), anyString(), eq(30_000L), anyString());
		assertThat(walletLedgerRepository.findAll())
			.extracting(WalletLedger::getLedgerType, WalletLedger::getAmount)
			.containsExactlyInAnyOrder(
				tuple(LedgerType.출금__PG결제취소_토스페이먼츠, -30_000L),
				tuple(LedgerType.출금__통장입금, -100_000L));
	}

	@Test
	@DisplayName("계좌로 보낼 몫(정산 대금)이 있는데 계좌 정보 없이 신청하면 400 WITHDRAWAL_ACCOUNT_REQUIRED로 거절하고 아무것도 바꾸지 않는다")
	void rejectsWithoutBankAccountWhenAccountPartExists() {
		Wallet wallet = createWallet(130_000);
		completedCharge(wallet, 30_000, "charge_1", LocalDateTime.of(2026, 10, 10, 9, 0));

		assertThatThrownBy(() -> cashFacade.requestWithdrawal(CUSTOMER_ID, null, null, null, "withdrawal_1", NOW))
			.isInstanceOf(WithdrawalAccountRequiredException.class)
			.hasFieldOrPropertyWithValue("code", "WITHDRAWAL_ACCOUNT_REQUIRED");

		assertThat(walletRepository.findById(CUSTOMER_ID).orElseThrow().getBalance()).isEqualTo(130_000);
		assertThat(walletLedgerRepository.findAll()).isEmpty();
		assertThat(walletWithdrawalRepository.findAll()).isEmpty();
		verify(paymentClient, never()).cancel(anyString(), anyString(), anyLong(), anyString());
	}

	@Test
	@DisplayName("같은 지갑에 처리 중인 환급이 있으면 새 신청은 409 WITHDRAWAL_IN_PROGRESS로 막고 아무것도 바꾸지 않는다")
	void rejectsWhileAnotherWithdrawalInProgress() {
		Wallet wallet = createWallet(30_000);
		completedCharge(wallet, 30_000, "charge_1", LocalDateTime.of(2026, 10, 10, 9, 0));
		WalletWithdrawal inProgress = new WalletWithdrawal(wallet, 10_000, null, null, null, "withdrawal_old");
		ReflectionTestUtils.setField(inProgress, "status", WithdrawalStatus.IN_PROGRESS);
		walletWithdrawalRepository.save(inProgress);

		assertThatThrownBy(() -> cashFacade.requestWithdrawal(CUSTOMER_ID, null, null, null, "withdrawal_new", NOW))
			.isInstanceOf(WithdrawalInProgressException.class)
			.hasFieldOrPropertyWithValue("code", "WITHDRAWAL_IN_PROGRESS");

		assertThat(walletRepository.findById(CUSTOMER_ID).orElseThrow().getBalance()).isEqualTo(30_000);
		assertThat(walletLedgerRepository.findAll()).isEmpty();
		verify(paymentClient, never()).cancel(anyString(), anyString(), anyLong(), anyString());
	}

	@Test
	@DisplayName("토스가 모두 성공하면 응답에 카드 30,000원·계좌 100,000원이 나뉘어 담기고, 처리 중·되돌림은 0원이다")
	void responseSplitsCardAndAccountAmounts() {
		Wallet wallet = createWallet(130_000);
		completedCharge(wallet, 30_000, "charge_1", LocalDateTime.of(2026, 10, 10, 9, 0));
		given(paymentClient.cancel(anyString(), anyString(), anyLong(), anyString()))
			.willReturn(new PaymentCancelResult(30_000, "txn_1"));

		CashWithdrawalResponseDto response = cashFacade.requestWithdrawal(
			CUSTOMER_ID, "004", "110123456789", "김러너", "withdrawal_1", NOW);

		assertThat(response.withdrawnAmount()).isEqualTo(130_000);
		assertThat(response.cardAmount()).isEqualTo(30_000);
		assertThat(response.accountAmount()).isEqualTo(100_000);
		assertThat(response.processingAmount()).isZero();
		assertThat(response.revertedAmount()).isZero();
	}

	@Test
	@DisplayName("토스가 4xx로 확실히 거절한 카드 몫은 예치금에 되돌리고 출금취소__PG결제취소_토스페이먼츠 줄을 남기며, 응답의 되돌림 금액에 담는다")
	void rejectedCardPartIsRevertedToBalance() {
		Wallet wallet = createWallet(30_000);
		completedCharge(wallet, 30_000, "charge_1", LocalDateTime.of(2026, 10, 10, 9, 0));
		given(paymentClient.cancel(anyString(), anyString(), anyLong(), anyString()))
			.willThrow(new PaymentCancelFailedException("PG사가 결제 취소를 거절했습니다.", "NOT_CANCELABLE_AMOUNT", false));

		CashWithdrawalResponseDto response =
			cashFacade.requestWithdrawal(CUSTOMER_ID, null, null, null, "withdrawal_1", NOW);

		assertThat(walletRepository.findById(CUSTOMER_ID).orElseThrow().getBalance()).isEqualTo(30_000);
		assertThat(walletLedgerRepository.findAll())
			.extracting(WalletLedger::getLedgerType, WalletLedger::getAmount)
			.containsExactlyInAnyOrder(
				tuple(LedgerType.출금__PG결제취소_토스페이먼츠, -30_000L),
				tuple(LedgerType.출금취소__PG결제취소_토스페이먼츠, 30_000L));
		assertThat(response.cardAmount()).isZero();
		assertThat(response.revertedAmount()).isEqualTo(30_000);
	}

	@Test
	@DisplayName("토스 결과를 모르는 카드 몫은 예치금에 되돌리지 않고 처리 중으로 남긴다 (환급 IN_PROGRESS, 응답 처리 중 금액)")
	void unknownCardPartStaysProcessing() {
		Wallet wallet = createWallet(30_000);
		completedCharge(wallet, 30_000, "charge_1", LocalDateTime.of(2026, 10, 10, 9, 0));
		given(paymentClient.cancel(anyString(), anyString(), anyLong(), anyString()))
			.willThrow(new PaymentCancelFailedException("PG 서버에 연결하지 못했습니다.", null, true));

		CashWithdrawalResponseDto response =
			cashFacade.requestWithdrawal(CUSTOMER_ID, null, null, null, "withdrawal_1", NOW);

		assertThat(walletRepository.findById(CUSTOMER_ID).orElseThrow().getBalance()).isZero();
		assertThat(walletLedgerRepository.findAll())
			.extracting(WalletLedger::getLedgerType, WalletLedger::getAmount)
			.containsExactly(tuple(LedgerType.출금__PG결제취소_토스페이먼츠, -30_000L));
		assertThat(walletWithdrawalRepository.findByIdempotencyKey("withdrawal_1").orElseThrow().getStatus())
			.isEqualTo(WithdrawalStatus.IN_PROGRESS);
		assertThat(response.processingAmount()).isEqualTo(30_000);
		assertThat(response.revertedAmount()).isZero();
		assertThat(response.cardAmount()).isZero();
	}

	@Test
	@DisplayName("결제취소가 성공하면 그 충전 건에 PG가 알려 준 취소 금액이 기록된다 (다음 환급 때 같은 몫을 다시 취소하지 않게)")
	void successfulCancelIsRecordedOnCharge() {
		Wallet wallet = createWallet(30_000);
		WalletCharge charge = completedCharge(wallet, 30_000, "charge_1", LocalDateTime.of(2026, 10, 10, 9, 0));
		given(paymentClient.cancel(anyString(), anyString(), anyLong(), anyString()))
			.willReturn(new PaymentCancelResult(30_000, "txn_1"));

		cashFacade.requestWithdrawal(CUSTOMER_ID, null, null, null, "withdrawal_1", NOW);

		assertThat(walletChargeRepository.findById(charge.getId()).orElseThrow().getCanceledAmount()).isEqualTo(30_000);
	}

	@Test
	@DisplayName("기한 안이었지만 토스가 환불 기간 초과(EXCEED_MAX_REFUND_DUE)로 거절한 카드 몫은 은행 정보가 있으면 계좌 송금으로 바꾼다")
	void refundPeriodRejectionSwitchesToAccount() {
		Wallet wallet = createWallet(30_000);
		completedCharge(wallet, 30_000, "charge_1", LocalDateTime.of(2026, 10, 10, 9, 0));
		given(paymentClient.cancel(anyString(), anyString(), anyLong(), anyString()))
			.willThrow(new PaymentCancelFailedException("PG사가 결제 취소를 거절했습니다.", "EXCEED_MAX_REFUND_DUE", false));

		CashWithdrawalResponseDto response = cashFacade.requestWithdrawal(
			CUSTOMER_ID, "004", "110123456789", "김러너", "withdrawal_1", NOW);

		assertThat(walletRepository.findById(CUSTOMER_ID).orElseThrow().getBalance()).isZero();
		assertThat(response.cardAmount()).isZero();
		assertThat(response.accountAmount()).isEqualTo(30_000);
		assertThat(response.revertedAmount()).isZero();
		assertThat(walletLedgerRepository.findAll())
			.extracting(WalletLedger::getLedgerType, WalletLedger::getAmount)
			.containsExactlyInAnyOrder(
				tuple(LedgerType.출금__PG결제취소_토스페이먼츠, -30_000L),
				tuple(LedgerType.출금취소__PG결제취소_토스페이먼츠, 30_000L),
				tuple(LedgerType.출금__통장입금, -30_000L));
	}

	@Test
	@DisplayName("환불 기간 초과로 거절된 카드 몫인데 은행 정보가 없으면 그 몫만 예치금에 되돌리고 응답에 계좌 정보가 필요하다고 표시한다")
	void refundPeriodRejectionWithoutBankAccountRevertsAndFlags() {
		Wallet wallet = createWallet(30_000);
		completedCharge(wallet, 30_000, "charge_1", LocalDateTime.of(2026, 10, 10, 9, 0));
		given(paymentClient.cancel(anyString(), anyString(), anyLong(), anyString()))
			.willThrow(new PaymentCancelFailedException("PG사가 결제 취소를 거절했습니다.", "EXCEED_MAX_REFUND_DUE", false));

		CashWithdrawalResponseDto response =
			cashFacade.requestWithdrawal(CUSTOMER_ID, null, null, null, "withdrawal_1", NOW);

		assertThat(walletRepository.findById(CUSTOMER_ID).orElseThrow().getBalance()).isEqualTo(30_000);
		assertThat(response.revertedAmount()).isEqualTo(30_000);
		assertThat(response.accountRequired()).isTrue();
	}

	@Test
	@DisplayName("토스가 부분 취소 불가(NOT_ALLOWED_PARTIAL_REFUND)로 거절한 카드 몫도 은행 정보가 있으면 계좌 송금으로 바꾼다")
	void partialRefundNotAllowedSwitchesToAccount() {
		Wallet wallet = createWallet(5_000);
		completedCharge(wallet, 10_000, "charge_1", LocalDateTime.of(2026, 10, 10, 9, 0));
		given(paymentClient.cancel(anyString(), anyString(), anyLong(), anyString()))
			.willThrow(new PaymentCancelFailedException(
				"PG사가 결제 취소를 거절했습니다.", "NOT_ALLOWED_PARTIAL_REFUND", false));

		CashWithdrawalResponseDto response = cashFacade.requestWithdrawal(
			CUSTOMER_ID, "004", "110123456789", "김러너", "withdrawal_1", NOW);

		assertThat(walletRepository.findById(CUSTOMER_ID).orElseThrow().getBalance()).isZero();
		assertThat(response.cardAmount()).isZero();
		assertThat(response.accountAmount()).isEqualTo(5_000);
		assertThat(response.revertedAmount()).isZero();
	}

	@Test
	@DisplayName("처리 중인 카드 몫을 재확인하면 신청 때와 같은 중복 방지 키로 결제취소를 다시 요청하고, 성공하면 환급을 COMPLETED로 확정한다")
	void reconcileRetriesUnknownPartWithSameKey() {
		Wallet wallet = createWallet(30_000);
		WalletCharge charge = completedCharge(wallet, 30_000, "charge_1", LocalDateTime.of(2026, 10, 10, 9, 0));
		given(paymentClient.cancel(anyString(), anyString(), anyLong(), anyString()))
			.willThrow(new PaymentCancelFailedException("PG 서버에 연결하지 못했습니다.", null, true))
			.willReturn(new PaymentCancelResult(30_000, "txn_1"));
		cashFacade.requestWithdrawal(CUSTOMER_ID, null, null, null, "withdrawal_1", NOW);

		cashWithdrawalReconcileUseCase.reconcile(NOW.plusMinutes(5));

		ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
		verify(paymentClient, times(2)).cancel(anyString(), anyString(), anyLong(), keys.capture());
		assertThat(keys.getAllValues().get(1)).isEqualTo(keys.getAllValues().get(0));
		assertThat(walletWithdrawalRepository.findByIdempotencyKey("withdrawal_1").orElseThrow().getStatus())
			.isEqualTo(WithdrawalStatus.COMPLETED);
		assertThat(walletChargeRepository.findById(charge.getId()).orElseThrow().getCanceledAmount()).isEqualTo(30_000);
		assertThat(walletRepository.findById(CUSTOMER_ID).orElseThrow().getBalance()).isZero();
	}

	@Test
	@DisplayName("재확인에서 토스가 확실히 거절하면 그 몫을 예치금에 되돌리고(환급 취소 원장 줄) 환급을 COMPLETED로 마무리한다")
	void reconcileRejectionRevertsPart() {
		Wallet wallet = createWallet(30_000);
		completedCharge(wallet, 30_000, "charge_1", LocalDateTime.of(2026, 10, 10, 9, 0));
		given(paymentClient.cancel(anyString(), anyString(), anyLong(), anyString()))
			.willThrow(new PaymentCancelFailedException("PG 서버에 연결하지 못했습니다.", null, true))
			.willThrow(new PaymentCancelFailedException("PG사가 결제 취소를 거절했습니다.", "NOT_CANCELABLE_AMOUNT", false));
		cashFacade.requestWithdrawal(CUSTOMER_ID, null, null, null, "withdrawal_1", NOW);

		cashWithdrawalReconcileUseCase.reconcile(NOW.plusMinutes(5));

		assertThat(walletRepository.findById(CUSTOMER_ID).orElseThrow().getBalance()).isEqualTo(30_000);
		assertThat(walletLedgerRepository.findAll())
			.extracting(WalletLedger::getLedgerType, WalletLedger::getAmount)
			.containsExactlyInAnyOrder(
				tuple(LedgerType.출금__PG결제취소_토스페이먼츠, -30_000L),
				tuple(LedgerType.출금취소__PG결제취소_토스페이먼츠, 30_000L));
		assertThat(walletWithdrawalRepository.findByIdempotencyKey("withdrawal_1").orElseThrow().getStatus())
			.isEqualTo(WithdrawalStatus.COMPLETED);
	}

	@Test
	@DisplayName("재확인에서도 결과를 모르는 몫은 되돌리지 않고 결과 모름 그대로 두며, 다른 몫의 재확인은 계속 진행한다")
	void reconcileKeepsUnknownAndContinuesOthers() {
		Wallet wallet = createWallet(30_000);
		WalletCharge chargeC = completedCharge(wallet, 10_000, "charge_c", LocalDateTime.of(2026, 10, 10, 9, 0));
		WalletCharge chargeD = completedCharge(wallet, 20_000, "charge_d", LocalDateTime.of(2026, 10, 13, 9, 0));
		PaymentCancelFailedException unknown =
			new PaymentCancelFailedException("PG 서버에 연결하지 못했습니다.", null, true);
		given(paymentClient.cancel(anyString(), anyString(), anyLong(), anyString())).willThrow(unknown);
		cashFacade.requestWithdrawal(CUSTOMER_ID, null, null, null, "withdrawal_1", NOW);
		given(paymentClient.cancel(anyString(), anyString(), anyLong(), eq("withdrawal_1:" + chargeC.getId())))
			.willThrow(unknown);
		given(paymentClient.cancel(anyString(), anyString(), anyLong(), eq("withdrawal_1:" + chargeD.getId())))
			.willReturn(new PaymentCancelResult(20_000, "txn_d"));

		cashWithdrawalReconcileUseCase.reconcile(NOW.plusMinutes(5));

		assertThat(walletRepository.findById(CUSTOMER_ID).orElseThrow().getBalance()).isZero();
		assertThat(walletChargeRepository.findById(chargeD.getId()).orElseThrow().getCanceledAmount())
			.isEqualTo(20_000);
		assertThat(walletChargeRepository.findById(chargeC.getId()).orElseThrow().getCanceledAmount()).isZero();
		assertThat(walletWithdrawalRepository.findByIdempotencyKey("withdrawal_1").orElseThrow().getStatus())
			.isEqualTo(WithdrawalStatus.IN_PROGRESS);
	}

	@Test
	@DisplayName("재확인에서 토스가 환불 기간 초과로 거절하면, 신청 때 받은 은행 정보로 그 몫을 계좌 송금으로 바꾸고 환급을 마무리한다")
	void reconcileRefundPeriodRejectionSwitchesToAccount() {
		Wallet wallet = createWallet(30_000);
		completedCharge(wallet, 30_000, "charge_1", LocalDateTime.of(2026, 10, 10, 9, 0));
		given(paymentClient.cancel(anyString(), anyString(), anyLong(), anyString()))
			.willThrow(new PaymentCancelFailedException("PG 서버에 연결하지 못했습니다.", null, true))
			.willThrow(new PaymentCancelFailedException("PG사가 결제 취소를 거절했습니다.", "EXCEED_MAX_REFUND_DUE", false));
		cashFacade.requestWithdrawal(CUSTOMER_ID, "004", "110123456789", "김러너", "withdrawal_1", NOW);

		cashWithdrawalReconcileUseCase.reconcile(NOW.plusMinutes(5));

		assertThat(walletRepository.findById(CUSTOMER_ID).orElseThrow().getBalance()).isZero();
		assertThat(walletLedgerRepository.findAll())
			.extracting(WalletLedger::getLedgerType, WalletLedger::getAmount)
			.containsExactlyInAnyOrder(
				tuple(LedgerType.출금__PG결제취소_토스페이먼츠, -30_000L),
				tuple(LedgerType.출금취소__PG결제취소_토스페이먼츠, 30_000L),
				tuple(LedgerType.출금__통장입금, -30_000L));
		assertThat(walletWithdrawalRepository.findByIdempotencyKey("withdrawal_1").orElseThrow().getStatus())
			.isEqualTo(WithdrawalStatus.COMPLETED);
	}

	@Test
	@DisplayName("결과 모름이 된 지 기준 시간(30분)이 넘었는데 재확인에서도 모르면 사람이 확인하도록 오류 로그를 남긴다")
	void reconcileLogsErrorWhenUnknownTooLong(CapturedOutput output) {
		Wallet wallet = createWallet(30_000);
		completedCharge(wallet, 30_000, "charge_1", LocalDateTime.of(2026, 10, 10, 9, 0));
		given(paymentClient.cancel(anyString(), anyString(), anyLong(), anyString()))
			.willThrow(new PaymentCancelFailedException("PG 서버에 연결하지 못했습니다.", null, true));
		cashFacade.requestWithdrawal(CUSTOMER_ID, null, null, null, "withdrawal_1", NOW);

		cashWithdrawalReconcileUseCase.reconcile(LocalDateTime.now().plusMinutes(31));

		assertThat(output).contains("환급 재확인", "수동 확인 필요");
	}

	@Test
	@DisplayName("토스를 부를 때는 예치금 차감이 이미 커밋돼 있다 (지갑 잠금을 잡은 채 토스를 기다리지 않는다)")
	void debitIsCommittedBeforeCallingToss() {
		Wallet wallet = createWallet(30_000);
		completedCharge(wallet, 30_000, "charge_1", LocalDateTime.of(2026, 10, 10, 9, 0));
		AtomicLong balanceSeenByOtherTransaction = new AtomicLong(-1);
		TransactionTemplate otherTransaction = new TransactionTemplate(transactionManager);
		otherTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		given(paymentClient.cancel(anyString(), anyString(), anyLong(), anyString())).willAnswer(invocation -> {
			balanceSeenByOtherTransaction.set(otherTransaction.execute(
				status -> walletRepository.findById(CUSTOMER_ID).orElseThrow().getBalance()));
			return new PaymentCancelResult(30_000, "txn_1");
		});

		cashFacade.requestWithdrawal(CUSTOMER_ID, null, null, null, "withdrawal_1", NOW);

		assertThat(balanceSeenByOtherTransaction.get()).isZero();
	}

	@Test
	@DisplayName("차감이 커밋된 뒤 결과를 반영하기 전에 서버가 멈춰도, 재확인이 남은 카드 몫을 같은 키로 이어서 마무리한다")
	void reconcileResumesPartLeftByCrash() {
		Wallet wallet = createWallet(30_000);
		WalletCharge charge = completedCharge(wallet, 30_000, "charge_1", LocalDateTime.of(2026, 10, 10, 9, 0));
		// 토스를 부르는 중에 서버가 멈춘 상황을 예상 못 한 예외로 흉내 낸다 (1단계는 이미 커밋됨)
		given(paymentClient.cancel(anyString(), anyString(), anyLong(), anyString()))
			.willThrow(new IllegalStateException("서버가 멈춤"))
			.willReturn(new PaymentCancelResult(30_000, "txn_1"));
		assertThatThrownBy(() -> cashFacade.requestWithdrawal(CUSTOMER_ID, null, null, null, "withdrawal_1", NOW))
			.isInstanceOf(IllegalStateException.class);

		cashWithdrawalReconcileUseCase.reconcile(LocalDateTime.now().plusMinutes(60));

		assertThat(walletRepository.findById(CUSTOMER_ID).orElseThrow().getBalance()).isZero();
		assertThat(walletChargeRepository.findById(charge.getId()).orElseThrow().getCanceledAmount()).isEqualTo(30_000);
		assertThat(walletWithdrawalRepository.findByIdempotencyKey("withdrawal_1").orElseThrow().getStatus())
			.isEqualTo(WithdrawalStatus.COMPLETED);
	}

	@Test
	@DisplayName("재확인은 몫마다 결과를 바로 커밋해, 다음 몫의 토스를 부를 때는 앞 몫의 되돌림이 이미 반영돼 있다")
	void reconcileCommitsEachPartBeforeNextTossCall() {
		Wallet wallet = createWallet(30_000);
		WalletCharge chargeC = completedCharge(wallet, 10_000, "charge_c", LocalDateTime.of(2026, 10, 10, 9, 0));
		WalletCharge chargeD = completedCharge(wallet, 20_000, "charge_d", LocalDateTime.of(2026, 10, 13, 9, 0));
		given(paymentClient.cancel(anyString(), anyString(), anyLong(), anyString()))
			.willThrow(new PaymentCancelFailedException("PG 서버에 연결하지 못했습니다.", null, true));
		cashFacade.requestWithdrawal(CUSTOMER_ID, null, null, null, "withdrawal_1", NOW);
		AtomicLong balanceSeenByOtherTransaction = new AtomicLong(-1);
		TransactionTemplate otherTransaction = new TransactionTemplate(transactionManager);
		otherTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		given(paymentClient.cancel(anyString(), anyString(), anyLong(), eq("withdrawal_1:" + chargeD.getId())))
			.willThrow(new PaymentCancelFailedException("PG사가 결제 취소를 거절했습니다.", "NOT_CANCELABLE_AMOUNT", false));
		given(paymentClient.cancel(anyString(), anyString(), anyLong(), eq("withdrawal_1:" + chargeC.getId())))
			.willAnswer(invocation -> {
				balanceSeenByOtherTransaction.set(otherTransaction.execute(
					status -> walletRepository.findById(CUSTOMER_ID).orElseThrow().getBalance()));
				return new PaymentCancelResult(10_000, "txn_c");
			});

		cashWithdrawalReconcileUseCase.reconcile(NOW.plusMinutes(5));

		assertThat(balanceSeenByOtherTransaction.get()).isEqualTo(20_000);
	}

	@Test
	@DisplayName("재확인은 결과 모름과 오래 남은 대기 몫을 섞어 몫이 생긴 시각이 오래된 것부터 처리한다")
	void reconcileProcessesOldestPartFirst() {
		Wallet wallet = createWallet(30_000);
		completedCharge(wallet, 10_000, "charge_c", LocalDateTime.of(2026, 10, 10, 9, 0));
		completedCharge(wallet, 20_000, "charge_d", LocalDateTime.of(2026, 10, 13, 9, 0));
		PaymentCancelFailedException unknown =
			new PaymentCancelFailedException("PG 서버에 연결하지 못했습니다.", null, true);
		// 신청 때 두 몫은 결과 모름, 재확인 때는 성공
		given(paymentClient.cancel(anyString(), anyString(), anyLong(), anyString()))
			.willThrow(unknown, unknown)
			.willAnswer(invocation -> new PaymentCancelResult(invocation.getArgument(2), "txn_default"));
		cashFacade.requestWithdrawal(CUSTOMER_ID, null, null, null, "withdrawal_1", NOW);
		// 먼저 저장된 몫은 결과 모름(최근), 나중에 저장된 몫은 서버가 멈춰 남은 대기(더 오래됨)로 만든다
		String olderKey = transactionTemplate.execute(status -> {
			List<WalletWithdrawalItem> items = walletWithdrawalItemRepository.findAll(Sort.by("id"));
			WalletWithdrawalItem older = items.get(1);
			ReflectionTestUtils.setField(older, "status", WithdrawalItemStatus.PENDING);
			ReflectionTestUtils.setField(older, "createDate", items.get(0).getCreateDate().minusMinutes(10));
			return older.getPgIdempotencyKey();
		});

		cashWithdrawalReconcileUseCase.reconcile(LocalDateTime.now().plusMinutes(60));

		ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
		verify(paymentClient, times(4)).cancel(anyString(), anyString(), anyLong(), keys.capture());
		assertThat(keys.getAllValues().get(2)).isEqualTo(olderKey);
	}

	@Test
	@DisplayName("재확인에서 토스가 3번 연달아 응답하지 않으면(연결 실패·타임아웃·5xx) 그 회차를 멈추고 남은 몫은 부르지 않는다")
	void reconcileStopsAfterThreeConsecutiveNoAnswers(CapturedOutput output) {
		createFourUnknownParts(new PaymentCancelFailedException("PG 서버에 연결하지 못했습니다.", null, true));

		cashWithdrawalReconcileUseCase.reconcile(NOW.plusMinutes(5));

		// 신청 때 4번 + 재확인 때 3번
		verify(paymentClient, times(7)).cancel(anyString(), anyString(), anyLong(), anyString());
		assertThat(output).contains("환급 재확인", "연달아", "멈춥니다");
	}

	@Test
	@DisplayName("같은 키가 아직 처리 중이라는 토스 응답(409)은 토스가 답한 것이라 연달아 실패로 세지 않고 다음 몫을 계속 부른다")
	void reconcileDoesNotCountIdempotentProcessingAsNoAnswer() {
		createFourUnknownParts(new PaymentCancelFailedException(
			"PG사가 결제 취소를 거절했습니다.", "IDEMPOTENT_REQUEST_PROCESSING", true));

		cashWithdrawalReconcileUseCase.reconcile(NOW.plusMinutes(5));

		// 신청 때 4번 + 재확인 때 4번
		verify(paymentClient, times(8)).cancel(anyString(), anyString(), anyLong(), anyString());
	}

	// 기한 안 충전 4건을 환급 신청하고, 신청 때와 재확인 때 토스가 모두 failure로 답하게 한다
	private void createFourUnknownParts(PaymentCancelFailedException failure) {
		Wallet wallet = createWallet(40_000);
		for (int day = 10; day <= 13; day++) {
			completedCharge(wallet, 10_000, "charge_" + day, LocalDateTime.of(2026, 10, day, 9, 0));
		}
		given(paymentClient.cancel(anyString(), anyString(), anyLong(), anyString())).willThrow(failure);
		cashFacade.requestWithdrawal(CUSTOMER_ID, null, null, null, "withdrawal_1", NOW);
	}

	private Wallet createWallet(long balance) {
		return transactionTemplate.execute(status -> {
			CashMember member = cashMemberRepository.save(new CashMember(CUSTOMER_ID, NOW, NOW, "러너3"));
			Wallet wallet = new Wallet(member);
			ReflectionTestUtils.setField(wallet, "balance", balance);
			return walletRepository.save(wallet);
		});
	}

	private WalletCharge completedCharge(Wallet wallet, long amount, String orderId, LocalDateTime approvedAt) {
		WalletCharge charge = new WalletCharge(wallet, amount, "TOSS", orderId);
		charge.markCompleted("toss_key_" + orderId, approvedAt, approvedAt);
		return walletChargeRepository.save(charge);
	}
}
