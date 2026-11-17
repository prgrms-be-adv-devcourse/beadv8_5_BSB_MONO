package com.bukang.cash.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.cash.domain.charge.ChargeStatus;
import com.bukang.cash.domain.withdrawal.WithdrawalItemStatus;
import com.bukang.cash.out.charge.WalletChargeRepository;
import com.bukang.cash.out.payout.PayoutTransferRepository;
import com.bukang.cash.out.recovery.RecoveryDepositRepository;
import com.bukang.cash.out.withdrawal.WalletWithdrawalItemRepository;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class CashStuckMoneyMetricsTest {
	private final WalletChargeRepository charges = mock(WalletChargeRepository.class);
	private final WalletWithdrawalItemRepository items = mock(WalletWithdrawalItemRepository.class);
	private final PayoutTransferRepository transfers = mock(PayoutTransferRepository.class);
	private final RecoveryDepositRepository deposits = mock(RecoveryDepositRepository.class);

	@Test
	@DisplayName("멈춘 돈 종류마다 지금 건수를 지표로 낸다")
	void exposesStuckCountPerKind() {
		given(charges.countByStatus(ChargeStatus.UNCERTAIN)).willReturn(3L);
		given(items.countByStatus(WithdrawalItemStatus.MANUAL_CHECK)).willReturn(1L);
		SimpleMeterRegistry registry = new SimpleMeterRegistry();

		new CashStuckMoneyMetrics(charges, items, transfers, deposits).bindTo(registry);

		assertThat(gauge(registry, "charge_uncertain")).isEqualTo(3);
		assertThat(gauge(registry, "withdrawal_manual_check")).isEqualTo(1);
		assertThat(gauge(registry, "payout_unknown")).isZero();
	}

	private double gauge(SimpleMeterRegistry registry, String kind) {
		return registry.get("cash.stuck.money").tag("kind", kind).gauge().value();
	}
}
