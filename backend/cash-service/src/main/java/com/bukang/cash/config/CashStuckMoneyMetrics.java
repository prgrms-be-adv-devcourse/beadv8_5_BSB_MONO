package com.bukang.cash.config;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.bukang.cash.domain.charge.ChargeStatus;
import com.bukang.cash.domain.payout.PayoutTransferStatus;
import com.bukang.cash.domain.recovery.RecoveryDepositStatus;
import com.bukang.cash.domain.withdrawal.WithdrawalItemStatus;
import com.bukang.cash.out.charge.WalletChargeRepository;
import com.bukang.cash.out.payout.PayoutTransferRepository;
import com.bukang.cash.out.recovery.RecoveryDepositRepository;
import com.bukang.cash.out.withdrawal.WalletWithdrawalItemRepository;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;

/**
 * 결과를 모르거나 사람이 확인해야 해서 멈춰 있는 돈의 건수를 지표로 낸다 (cash_stuck_money{kind=...})
 * Prometheus가 가져갈 때마다(15초) 상태별로 센다. 늘어나면 PG 장애나 재확인 배치 고장을 의심한다
 */
@Component
@RequiredArgsConstructor
public class CashStuckMoneyMetrics implements MeterBinder {
	private final WalletChargeRepository walletChargeRepository;
	private final WalletWithdrawalItemRepository walletWithdrawalItemRepository;
	private final PayoutTransferRepository payoutTransferRepository;
	private final RecoveryDepositRepository recoveryDepositRepository;

	@Override
	public void bindTo(MeterRegistry registry) {
		stuck(registry, "charge_uncertain", () -> walletChargeRepository.countByStatus(ChargeStatus.UNCERTAIN));
		stuck(registry, "withdrawal_manual_check",
			() -> walletWithdrawalItemRepository.countByStatus(WithdrawalItemStatus.MANUAL_CHECK));
		stuck(registry, "payout_unknown", () -> payoutTransferRepository.countByStatus(PayoutTransferStatus.UNKNOWN));
		stuck(registry, "recovery_expired",
			() -> recoveryDepositRepository.countByStatus(RecoveryDepositStatus.EXPIRED));
		stuck(registry, "recovery_unsent",
			() -> recoveryDepositRepository.countByStatusAndSentAtIsNull(RecoveryDepositStatus.DEPOSITED));
	}

	private static void stuck(MeterRegistry registry, String kind, Supplier<Number> count) {
		Gauge.builder("cash.stuck.money", count)
			.tag("kind", kind)
			.description("멈춰 있는 돈의 건수")
			.register(registry);
	}
}
