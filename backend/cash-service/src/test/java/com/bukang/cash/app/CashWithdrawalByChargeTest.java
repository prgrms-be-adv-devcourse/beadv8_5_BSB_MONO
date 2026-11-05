package com.bukang.cash.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
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
import com.bukang.cash.domain.withdrawal.WithdrawalStatus;
import com.bukang.cash.dto.CashWithdrawalResponseDto;
import com.bukang.cash.out.CashMemberRepository;
import com.bukang.cash.out.WalletChargeRepository;
import com.bukang.cash.out.WalletLedgerRepository;
import com.bukang.cash.out.WalletRepository;
import com.bukang.cash.out.WalletWithdrawalRepository;
import com.bukang.cash.out.pg.PaymentCancelResult;
import com.bukang.cash.out.pg.PaymentClient;

// 정책 5장 2차 1 「PG 결제취소와 기한」대로 환급을 충전 건별 PG 결제취소와 계좌 송금으로 나누는 흐름.
// 토스는 Mockito 목으로 바꿔 결과를 직접 정한다. 지갑 잔액은 ReflectionTestUtils로 넣는다
@SpringBootTest
@ActiveProfiles("test")
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

	@MockitoBean
	private PaymentClient paymentClient;

	@AfterEach
	void tearDown() {
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
