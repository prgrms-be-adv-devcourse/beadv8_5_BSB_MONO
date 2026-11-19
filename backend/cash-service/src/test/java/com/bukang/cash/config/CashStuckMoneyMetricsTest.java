package com.bukang.cash.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.cash.domain.charge.ChargeStatus;
import com.bukang.cash.domain.payout.PayoutTransferStatus;
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
	private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
	private final CashStuckMoneyMetrics metrics = new CashStuckMoneyMetrics(charges, items, transfers, deposits);

	@Test
	@DisplayName("멈춘 돈 종류마다 마지막으로 센 건수를 지표로 낸다")
	void exposesStuckCountPerKind() {
		given(charges.countByStatus(ChargeStatus.UNCERTAIN)).willReturn(3L);
		given(items.countByStatus(WithdrawalItemStatus.MANUAL_CHECK)).willReturn(1L);
		metrics.bindTo(registry);

		metrics.refresh();

		assertThat(gauge("charge_uncertain")).isEqualTo(3);
		assertThat(gauge("withdrawal_manual_check")).isEqualTo(1);
		assertThat(gauge("payout_unknown")).isZero();
	}

	@Test
	@DisplayName("지표를 읽을 때는 DB를 조회하지 않는다 (DB 연결이 다 차도 지표 수집이 막히지 않게)")
	void readingGaugeDoesNotQueryDb() {
		metrics.bindTo(registry);

		double value = gauge("charge_uncertain");

		assertThat(value).isNaN();
		verifyNoInteractions(charges, items, transfers, deposits);
	}

	@Test
	@DisplayName("세다 실패한 종류는 옛 값 대신 값 없음으로 둔다")
	void failedCountBecomesNaN() {
		given(transfers.countByStatus(PayoutTransferStatus.UNKNOWN)).willReturn(2L);
		metrics.bindTo(registry);
		metrics.refresh();
		given(transfers.countByStatus(PayoutTransferStatus.UNKNOWN)).willThrow(new IllegalStateException("DB 연결 없음"));

		metrics.refresh();

		assertThat(gauge("payout_unknown")).isNaN();
		assertThat(gauge("charge_uncertain")).isZero();
	}

	private double gauge(String kind) {
		return registry.get("cash.stuck.money").tag("kind", kind).gauge().value();
	}
}
