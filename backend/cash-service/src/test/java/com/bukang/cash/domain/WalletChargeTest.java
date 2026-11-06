package com.bukang.cash.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.cash.domain.charge.WalletCharge;
import com.bukang.cash.domain.member.CashMember;
import com.bukang.cash.domain.wallet.Wallet;

class WalletChargeTest {
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 10, 0);

	private WalletCharge completedCharge(long amount) {
		Wallet wallet = new Wallet(new CashMember(3, NOW, NOW, "러너3"));
		WalletCharge charge = new WalletCharge(wallet, amount, "TOSS", "order_1");
		charge.markCompleted("toss_key_1", NOW, NOW);
		return charge;
	}

	@Test
	@DisplayName("10,000원 충전 건에서 5,000원을 PG 결제취소하면 그 건의 취소한 금액이 5,000원이 된다")
	void recordCancelAddsCanceledAmount() {
		WalletCharge charge = completedCharge(10_000);

		charge.recordCancel(5_000);

		assertThat(charge.getCanceledAmount()).isEqualTo(5_000);
	}

	@Test
	@DisplayName("10,000원 충전 건에서 이미 5,000원을 취소했으면 6,000원을 더 취소하려 할 때 거절하고, 취소한 금액은 그대로다")
	void recordCancelRejectsMoreThanRemaining() {
		WalletCharge charge = completedCharge(10_000);
		charge.recordCancel(5_000);

		assertThatThrownBy(() -> charge.recordCancel(6_000))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(charge.getCanceledAmount()).isEqualTo(5_000);
	}
}
