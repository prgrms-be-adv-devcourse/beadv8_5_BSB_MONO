package com.bukang.cash.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.cash.domain.charge.WalletCharge;
import com.bukang.cash.domain.member.CashMember;
import com.bukang.cash.domain.payment.Payment;
import com.bukang.cash.domain.payout.PayoutTransfer;
import com.bukang.cash.domain.payout.PayoutTransferType;
import com.bukang.cash.domain.wallet.LedgerRefType;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.domain.wallet.WalletLedger;
import com.bukang.cash.out.charge.WalletChargeRepository;
import com.bukang.cash.out.member.CashMemberRepository;
import com.bukang.cash.out.payment.PaymentRepository;
import com.bukang.cash.out.payout.PayoutTransferRepository;
import com.bukang.cash.out.wallet.WalletLedgerRepository;
import com.bukang.cash.out.wallet.WalletRepository;

// DB 제약(UNIQUE, NOT NULL)이 실제로 걸려 있는지 H2에 저장해 확인한다
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CashEntityConstraintTest {
	@Autowired
	private CashMemberRepository cashMemberRepository;
	@Autowired
	private WalletRepository walletRepository;
	@Autowired
	private WalletChargeRepository walletChargeRepository;
	@Autowired
	private PaymentRepository paymentRepository;
	@Autowired
	private WalletLedgerRepository walletLedgerRepository;
	@Autowired
	private PayoutTransferRepository payoutTransferRepository;

	private Wallet wallet;

	@BeforeEach
	void setUp() {
		LocalDateTime now = LocalDateTime.of(2026, 10, 12, 10, 0);
		CashMember member = cashMemberRepository.save(new CashMember(3, now, now, "러너3"));
		wallet = walletRepository.save(new Wallet(member));
	}

	@Test
	@DisplayName("충전 멱등키가 같으면 두 번째 충전은 저장되지 않는다")
	void duplicateChargeIdempotencyKey() {
		walletChargeRepository.saveAndFlush(new WalletCharge(wallet, 10_000, "TOSS", "charge-key-1"));

		assertThatThrownBy(() ->
			walletChargeRepository.saveAndFlush(new WalletCharge(wallet, 20_000, "TOSS", "charge-key-1"))
		).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("같은 주문 ID로 두 번째 결제는 저장되지 않는다")
	void duplicateOrderIdPayment() {
		paymentRepository.saveAndFlush(new Payment(15, wallet, 5_000));

		assertThatThrownBy(() ->
			paymentRepository.saveAndFlush(new Payment(15, wallet, 5_000))
		).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("원장 유형이 없으면 저장되지 않는다")
	void ledgerTypeRequired() {
		WalletLedger ledger = new WalletLedger(wallet, wallet.getHolder(), null, 1_000, 1_000,
			LedgerRefType.WalletCharge, 1);

		assertThatThrownBy(() -> walletLedgerRepository.saveAndFlush(ledger))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("충전의 PG사가 없으면 저장되지 않는다")
	void chargePgProviderRequired() {
		assertThatThrownBy(() ->
			walletChargeRepository.saveAndFlush(new WalletCharge(wallet, 10_000, null, "charge-key-2"))
		).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("같은 정산 ID·이동 종류로 두 번째 정산 이동 기록은 저장되지 않는다")
	void duplicatePayoutIdAndTransferType() {
		payoutTransferRepository.saveAndFlush(new PayoutTransfer(500, 10, PayoutTransferType.SETTLEMENT, 90_000));

		assertThatThrownBy(() ->
			payoutTransferRepository.saveAndFlush(new PayoutTransfer(500, 10, PayoutTransferType.SETTLEMENT, 90_000))
		).isInstanceOf(DataIntegrityViolationException.class);
	}
}
