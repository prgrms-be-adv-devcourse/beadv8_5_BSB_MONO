package com.bukang.cash.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.cash.domain.exception.WalletNotFoundException;
import com.bukang.cash.domain.member.CashMember;
import com.bukang.cash.domain.wallet.LedgerRefType;
import com.bukang.cash.domain.wallet.LedgerType;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.domain.wallet.WalletLedger;
import com.bukang.cash.dto.WalletBalanceResponseDto;
import com.bukang.cash.out.CashMemberRepository;
import com.bukang.cash.out.WalletLedgerRepository;
import com.bukang.cash.out.WalletRepository;

import jakarta.persistence.EntityManager;

// 지갑·원장 동작만 확인하기 위해 syncMember는 거치지 않고, CashMember를 직접 저장한 뒤 지갑 흐름을 확인한다
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CashWalletTest {
	private static final int MEMBER_ID = 3;

	@Autowired
	private CashFacade cashFacade;
	@Autowired
	private CashMemberRepository cashMemberRepository;
	@Autowired
	private WalletRepository walletRepository;
	@Autowired
	private WalletLedgerRepository walletLedgerRepository;
	@Autowired
	private EntityManager entityManager;

	@BeforeEach
	void setUp() {
		LocalDateTime now = LocalDateTime.of(2026, 10, 12, 10, 0);
		cashMemberRepository.save(new CashMember(MEMBER_ID, now, now, "러너3"));
	}

	@Test
	@DisplayName("지갑을 만들면 잔액 0원으로 저장된다")
	void createWallet() {
		cashFacade.ensureWallet(MEMBER_ID);
		entityManager.flush();
		entityManager.clear();

		Wallet wallet = walletRepository.findById(MEMBER_ID).orElseThrow();
		assertThat(wallet.getBalance()).isZero();
		assertThat(wallet.getHolder().getId()).isEqualTo(MEMBER_ID);
	}

	@Test
	@DisplayName("같은 회원으로 지갑을 두 번 만들어도 지갑은 하나만 남는다")
	void createWalletTwice() {
		Wallet first = cashFacade.ensureWallet(MEMBER_ID);
		Wallet second = cashFacade.ensureWallet(MEMBER_ID);

		assertThat(second.getId()).isEqualTo(first.getId());
		assertThat(walletRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("회원 ID로 현재 잔액을 조회한다")
	void getBalance() {
		Wallet wallet = cashFacade.ensureWallet(MEMBER_ID);
		walletLedgerRepository.save(
			wallet.credit(10_000, LedgerType.충전__PG결제_토스페이먼츠, LedgerRefType.WalletCharge, 1)
		);
		entityManager.flush();
		entityManager.clear();

		WalletBalanceResponseDto balance = cashFacade.getBalance(MEMBER_ID);

		assertThat(balance.holderId()).isEqualTo(MEMBER_ID);
		assertThat(balance.balance()).isEqualTo(10_000);
	}

	@Test
	@DisplayName("지갑이 없는 회원의 잔액을 조회하면 WalletNotFoundException이 난다")
	void getBalanceWithoutWallet() {
		assertThatThrownBy(() -> cashFacade.getBalance(999))
			.isInstanceOf(WalletNotFoundException.class)
			.hasMessage("존재하지 않는 지갑입니다.");
	}

	@Test
	@DisplayName("원장의 유형·증감 금액·거래 후 잔액·참조 ID가 그대로 저장되고 조회된다")
	void saveAndFindLedger() {
		Wallet wallet = cashFacade.ensureWallet(MEMBER_ID);
		walletLedgerRepository.save(
			wallet.credit(10_000, LedgerType.충전__PG결제_토스페이먼츠, LedgerRefType.WalletCharge, 1)
		);
		int ledgerId = walletLedgerRepository.save(
			wallet.debit(3_000, LedgerType.사용__주문결제, LedgerRefType.Order, 15)
		).getId();
		entityManager.flush();
		entityManager.clear();

		WalletLedger ledger = walletLedgerRepository.findById(ledgerId).orElseThrow();
		assertThat(ledger.getLedgerType()).isEqualTo(LedgerType.사용__주문결제);
		assertThat(ledger.getAmount()).isEqualTo(-3_000);
		assertThat(ledger.getBalanceAfter()).isEqualTo(7_000);
		assertThat(ledger.getRefTypeCode()).isEqualTo(LedgerRefType.Order);
		assertThat(ledger.getRefId()).isEqualTo(15);
		assertThat(ledger.getWallet().getId()).isEqualTo(MEMBER_ID);
		assertThat(ledger.getHolder().getId()).isEqualTo(MEMBER_ID);
		assertThat(walletRepository.findById(MEMBER_ID).orElseThrow().getBalance()).isEqualTo(7_000);
	}
}
