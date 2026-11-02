package com.bukang.cash.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.cash.domain.charge.WalletCharge;
import com.bukang.cash.domain.member.CashMember;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.domain.withdrawal.WalletWithdrawal;
import com.bukang.cash.out.CashMemberRepository;
import com.bukang.cash.out.WalletChargeRepository;
import com.bukang.cash.out.WalletRepository;
import com.bukang.cash.out.WalletWithdrawalRepository;

import jakarta.persistence.EntityManager;

// EncryptedStringConverter가 실제로 DB엔 암호문을 저장하고, 엔티티로 읽을 땐 평문으로 복호화하는지 확인한다
@SpringBootTest
@ActiveProfiles("test")
class EncryptedFieldsTest {
	private static final long CUSTOMER_ID = 5;

	@Autowired
	private CashMemberRepository cashMemberRepository;
	@Autowired
	private WalletRepository walletRepository;
	@Autowired
	private WalletChargeRepository walletChargeRepository;
	@Autowired
	private WalletWithdrawalRepository walletWithdrawalRepository;
	@Autowired
	private TransactionTemplate transactionTemplate;
	@Autowired
	private EntityManager entityManager;

	@AfterEach
	void tearDown() {
		walletChargeRepository.deleteAll();
		walletWithdrawalRepository.deleteAll();
		walletRepository.deleteAll();
		cashMemberRepository.deleteAll();
	}

	@Test
	@DisplayName("WalletCharge.pgPaymentKey는 DB엔 암호문으로 저장되고, 조회하면 평문으로 복호화된다")
	void pgPaymentKeyIsEncryptedAtRest() {
		transactionTemplate.executeWithoutResult(status -> {
			Wallet wallet = createWallet();
			WalletCharge charge = walletChargeRepository.save(new WalletCharge(wallet, 10_000, "TOSS", "order_1"));
			charge.markCompleted("toss_key_plain", LocalDateTime.now());
			entityManager.flush();

			String raw = (String) entityManager.createNativeQuery(
				"SELECT pg_payment_key FROM cash_wallet_charge WHERE id = :id")
				.setParameter("id", charge.getId())
				.getSingleResult();
			assertThat(raw).isNotEqualTo("toss_key_plain");

			entityManager.clear();
			WalletCharge reloaded = walletChargeRepository.findById(charge.getId()).orElseThrow();
			assertThat(reloaded.getPgPaymentKey()).isEqualTo("toss_key_plain");
		});
	}

	@Test
	@DisplayName("WalletWithdrawal.accountNumber는 DB엔 암호문으로 저장되고, 조회하면 평문으로 복호화된다")
	void accountNumberIsEncryptedAtRest() {
		transactionTemplate.executeWithoutResult(status -> {
			Wallet wallet = createWallet();
			WalletWithdrawal withdrawal = walletWithdrawalRepository.save(
				new WalletWithdrawal(wallet, 10_000, "004", "110123456789", "홍길동", "withdrawal_1"));
			entityManager.flush();

			String raw = (String) entityManager.createNativeQuery(
				"SELECT account_number FROM cash_wallet_withdrawal WHERE id = :id")
				.setParameter("id", withdrawal.getId())
				.getSingleResult();
			assertThat(raw).isNotEqualTo("110123456789");

			entityManager.clear();
			WalletWithdrawal reloaded = walletWithdrawalRepository.findById(withdrawal.getId()).orElseThrow();
			assertThat(reloaded.getAccountNumber()).isEqualTo("110123456789");
		});
	}

	private Wallet createWallet() {
		LocalDateTime now = LocalDateTime.of(2026, 10, 16, 10, 0);
		CashMember member = cashMemberRepository.save(new CashMember(CUSTOMER_ID, now, now, "러너5"));
		return walletRepository.save(new Wallet(member));
	}
}
