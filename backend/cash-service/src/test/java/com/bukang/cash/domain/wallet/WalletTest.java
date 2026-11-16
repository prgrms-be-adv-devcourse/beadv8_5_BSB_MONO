package com.bukang.cash.domain.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.cash.domain.member.CashMember;

class WalletTest {
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 16, 10, 0);

	@Test
	@DisplayName("회원·시스템 지갑은 잔액보다 많이 빼면 거절하고 잔액을 그대로 둔다")
	void debitMoreThanBalanceIsRejected() {
		Wallet wallet = walletWithBalance(10_000);

		assertThatThrownBy(() -> wallet.debit(10_001, LedgerType.사용__주문결제, LedgerRefType.Order, 1))
			.isInstanceOf(IllegalStateException.class);
		assertThat(wallet.getBalance()).isEqualTo(10_000);
	}

	@Test
	@DisplayName("잔액과 같은 금액은 뺄 수 있어 잔액이 0이 된다")
	void debitExactBalance() {
		Wallet wallet = walletWithBalance(10_000);

		WalletLedger ledger = wallet.debit(10_000, LedgerType.사용__주문결제, LedgerRefType.Order, 1);

		assertThat(wallet.getBalance()).isZero();
		assertThat(ledger.getAmount()).isEqualTo(-10_000);
	}

	@Test
	@DisplayName("홀딩 지갑 차감은 1차 정산 뒤 환불처럼 잔액이 모자라도 음수로 뺀다")
	void holdingDebitCanGoNegative() {
		Wallet holding = walletWithBalance(10_000);

		WalletLedger ledger = holding.debitAllowingNegative(
			30_000, LedgerType.임시보관반환__주문취소, LedgerRefType.Order, 1);

		assertThat(holding.getBalance()).isEqualTo(-20_000);
		assertThat(ledger.getBalanceAfter()).isEqualTo(-20_000);
	}

	private Wallet walletWithBalance(long balance) {
		Wallet wallet = new Wallet(new CashMember(7L, NOW, NOW, "회원"));
		wallet.credit(balance, LedgerType.충전__PG결제_토스페이먼츠, LedgerRefType.WalletCharge, 1);
		return wallet;
	}
}
