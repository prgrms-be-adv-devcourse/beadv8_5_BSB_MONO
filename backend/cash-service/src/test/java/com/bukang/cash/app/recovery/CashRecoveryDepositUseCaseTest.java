package com.bukang.cash.app.recovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.cash.app.CashFacade;
import com.bukang.cash.config.CashWalletProperties;
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
class CashRecoveryDepositUseCaseTest {
	private static final String CLAIM_CODE = "RC-20261214-0A3F";
	private static final String SECRET = "ps_secret_1";
	private static final LocalDateTime DEPOSITED_AT = LocalDateTime.of(2026, 12, 20, 14, 2);

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

		cashFacade.receiveRecoveryDeposit(CLAIM_CODE, SECRET);

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

		cashFacade.receiveRecoveryDeposit(CLAIM_CODE, "forged_secret");

		verify(paymentClient, never()).lookupByOrderId(anyString());
		assertNothingRecorded();
	}

	@Test
	@DisplayName("회수 입금 기록에 없는 주문번호의 알림이면 오류 없이 끝내고, PG 조회도 기록도 하지 않는다")
	void ignoresCallbackForUnknownOrder() {
		assertThatCode(() -> cashFacade.receiveRecoveryDeposit("RC-20261214-FFFF", SECRET))
			.doesNotThrowAnyException();

		verify(paymentClient, never()).lookupByOrderId(anyString());
		assertNothingRecorded();
	}

	private void assertNothingRecorded() {
		assertThat(walletRepository.findById(cashWalletProperties.holdingMemberId()).orElseThrow().getBalance())
			.isZero();
		assertThat(walletLedgerRepository.count()).isZero();
		assertThat(recoveryDepositRepository.findById(deposit.getId()).orElseThrow().getStatus())
			.isEqualTo(RecoveryDepositStatus.WAITING);
	}
}
