package com.bukang.cash.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
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
import lombok.extern.slf4j.Slf4j;

/**
 * 결과를 모르거나 사람이 확인해야 해서 멈춰 있는 돈의 건수를 지표로 낸다 (cash_stuck_money{kind=...})
 * 늘어나면 PG 장애나 재확인 배치 고장을 의심한다
 * 건수는 cash.stuck-money.refresh-interval-millis마다 따로 세어 두고, 지표를 가져갈 때는 센 값만 읽는다.
 * 가져갈 때마다 DB를 조회하면 DB 연결이 다 찬 동안 지표 수집이 연결을 기다리다 시간을 넘겨,
 * 그 순간의 앱 지표가 통째로 빠진다 (2차 부하 측정 B-7)
 */
@Slf4j
@Profile("!test")
@Component
public class CashStuckMoneyMetrics implements MeterBinder {
	private final Map<String, LongSupplier> counters = new LinkedHashMap<>();
	// 종류별 마지막으로 센 건수. 아직 세지 않았거나 세다 실패하면 값 없음(NaN)
	private final Map<String, Double> counts = new ConcurrentHashMap<>();

	public CashStuckMoneyMetrics(
		WalletChargeRepository walletChargeRepository,
		WalletWithdrawalItemRepository walletWithdrawalItemRepository,
		PayoutTransferRepository payoutTransferRepository,
		RecoveryDepositRepository recoveryDepositRepository
	) {
		counters.put("charge_uncertain", () -> walletChargeRepository.countByStatus(ChargeStatus.UNCERTAIN));
		counters.put("withdrawal_manual_check",
			() -> walletWithdrawalItemRepository.countByStatus(WithdrawalItemStatus.MANUAL_CHECK));
		counters.put("payout_unknown", () -> payoutTransferRepository.countByStatus(PayoutTransferStatus.UNKNOWN));
		counters.put("recovery_expired",
			() -> recoveryDepositRepository.countByStatus(RecoveryDepositStatus.EXPIRED));
		counters.put("recovery_unsent",
			() -> recoveryDepositRepository.countByStatusAndSentAtIsNull(RecoveryDepositStatus.DEPOSITED));
	}

	@Override
	public void bindTo(MeterRegistry registry) {
		for (String kind : counters.keySet()) {
			Gauge.builder("cash.stuck.money", counts, c -> c.getOrDefault(kind, Double.NaN))
				.tag("kind", kind)
				.description("멈춰 있는 돈의 건수")
				.register(registry);
		}
	}

	@Scheduled(fixedDelayString = "${cash.stuck-money.refresh-interval-millis}")
	public void refresh() {
		// 한 종류가 실패해도 나머지는 센다. 실패한 종류는 옛 값을 보여 주지 않게 값 없음으로 둔다
		counters.forEach((kind, counter) -> {
			try {
				counts.put(kind, (double)counter.getAsLong());
			} catch (RuntimeException e) {
				counts.put(kind, Double.NaN);
				log.warn("멈춘 돈 건수를 세지 못함 kind={}", kind, e);
			}
		});
	}
}
