package com.bukang.cash.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.cash.domain.exception.WalletBalanceEmptyException;
import com.bukang.cash.domain.exception.WalletNotFoundException;
import com.bukang.cash.domain.exception.WithdrawalMemberMismatchException;
import com.bukang.cash.domain.member.CashMember;
import com.bukang.cash.domain.wallet.LedgerRefType;
import com.bukang.cash.domain.wallet.LedgerType;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.domain.wallet.WalletLedger;
import com.bukang.cash.domain.withdrawal.WalletWithdrawal;
import com.bukang.cash.domain.withdrawal.WithdrawalStatus;
import com.bukang.cash.dto.CashWithdrawalResponseDto;
import com.bukang.cash.out.CashMemberRepository;
import com.bukang.cash.out.WalletLedgerRepository;
import com.bukang.cash.out.WalletRepository;
import com.bukang.cash.out.WalletWithdrawalRepository;

// 지갑 잔액은 ReflectionTestUtils로 직접 세팅한다. wallet.credit(...)으로 만들면
// 그 자체가 장부 1줄을 남겨서, 장부 개수를 확인하는 테스트와 섞이기 때문이다.
@SpringBootTest
@ActiveProfiles("test")
class CashWithdrawalUseCaseTest {
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
	@DisplayName("환급 신청하면 잔액이 0이 되고, 환급 기록이 COMPLETED로 남는다")
	void requestWithdrawalZeroesBalanceAndCompletes() {
		createCustomerWallet(50_000);

		CashWithdrawalResponseDto response = cashFacade.requestWithdrawal(
			CUSTOMER_ID, "004", "110123456789", "홍길동", "withdrawal_1");

		assertThat(response.withdrawnAmount()).isEqualTo(50_000);
		assertThat(response.balance()).isEqualTo(0);
		assertBalance(CUSTOMER_ID, 0);

		WalletWithdrawal withdrawal = walletWithdrawalRepository.findByIdempotencyKey("withdrawal_1").orElseThrow();
		assertThat(withdrawal.getStatus()).isEqualTo(WithdrawalStatus.COMPLETED);
		assertThat(withdrawal.getAmount()).isEqualTo(50_000);
	}

	@Test
	@DisplayName("승인 성공 시 장부 1줄(출금__통장입금)이 남는다")
	void withdrawalLeavesOneLedgerEntry() {
		createCustomerWallet(30_000);

		cashFacade.requestWithdrawal(CUSTOMER_ID, "004", "110123456789", "홍길동", "withdrawal_1");

		assertThat(walletLedgerRepository.findAll()).hasSize(1);
		WalletLedger ledger = walletLedgerRepository.findAll().get(0);
		assertThat(ledger.getLedgerType()).isEqualTo(LedgerType.출금__통장입금);
		assertThat(ledger.getAmount()).isEqualTo(-30_000);
		assertThat(ledger.getRefTypeCode()).isEqualTo(LedgerRefType.Withdrawal);
	}

	@Test
	@DisplayName("같은 idempotencyKey로 다시 요청하면 한 번만 처리되고 같은 결과를 돌려준다")
	void sameIdempotencyKeyIsProcessedOnlyOnce() {
		createCustomerWallet(20_000);

		CashWithdrawalResponseDto first = cashFacade.requestWithdrawal(
			CUSTOMER_ID, "004", "110123456789", "홍길동", "withdrawal_1");
		CashWithdrawalResponseDto second = cashFacade.requestWithdrawal(
			CUSTOMER_ID, "004", "110123456789", "홍길동", "withdrawal_1");

		assertThat(second.withdrawnAmount()).isEqualTo(first.withdrawnAmount());
		assertThat(second.balance()).isEqualTo(first.balance());
		assertThat(walletWithdrawalRepository.findAll()).hasSize(1);
		assertThat(walletLedgerRepository.findAll()).hasSize(1);
	}

	@Test
	@DisplayName("잔액이 0원이면 거절하고, 환급 기록도 남기지 않는다")
	void rejectsWhenBalanceIsEmpty() {
		createCustomerWallet(0);

		assertThatThrownBy(() ->
			cashFacade.requestWithdrawal(CUSTOMER_ID, "004", "110123456789", "홍길동", "withdrawal_1"))
			.isInstanceOf(WalletBalanceEmptyException.class);

		assertThat(walletWithdrawalRepository.findByIdempotencyKey("withdrawal_1")).isEmpty();
		assertThat(walletLedgerRepository.findAll()).isEmpty();
	}

	@Test
	@DisplayName("지갑이 없으면 404 예외로 알린다")
	void walletNotFound() {
		assertThatThrownBy(() -> cashFacade.requestWithdrawal(999, "004", "110123456789", "홍길동", "withdrawal_1"))
			.isInstanceOf(WalletNotFoundException.class);
	}

	@Test
	@DisplayName("다른 회원의 idempotencyKey로 요청하면 거절한다")
	void rejectsWhenIdempotencyKeyBelongsToAnotherMember() {
		long otherMemberId = 4;
		Wallet otherWallet = createWallet(otherMemberId, 0);
		WalletWithdrawal otherWithdrawal = walletWithdrawalRepository.save(
			new WalletWithdrawal(otherWallet, 10_000, "004", "110123456789", "홍길동", "withdrawal_1"));
		ReflectionTestUtils.setField(otherWithdrawal, "status", WithdrawalStatus.COMPLETED);
		walletWithdrawalRepository.saveAndFlush(otherWithdrawal);

		createCustomerWallet(0);

		assertThatThrownBy(() ->
			cashFacade.requestWithdrawal(CUSTOMER_ID, "004", "110123456789", "홍길동", "withdrawal_1"))
			.isInstanceOf(WithdrawalMemberMismatchException.class);
	}

	private void createCustomerWallet(long initialBalance) {
		createWallet(CUSTOMER_ID, initialBalance);
	}

	private Wallet createWallet(long memberId, long initialBalance) {
		return transactionTemplate.execute(status -> {
			LocalDateTime now = LocalDateTime.of(2026, 10, 15, 10, 0);
			CashMember member = cashMemberRepository.save(new CashMember(memberId, now, now, "러너" + memberId));
			Wallet wallet = new Wallet(member);
			ReflectionTestUtils.setField(wallet, "balance", initialBalance);
			return walletRepository.save(wallet);
		});
	}

	private void assertBalance(long holderId, int expected) {
		assertThat(walletRepository.findById(holderId).orElseThrow().getBalance()).isEqualTo(expected);
	}
}
