package com.bukang.cash.app;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.cash.config.ChargeReconcileProperties;
import com.bukang.cash.domain.charge.ChargeStatus;
import com.bukang.cash.domain.charge.WalletCharge;
import com.bukang.cash.domain.exception.PaymentLookupFailedException;
import com.bukang.cash.domain.wallet.LedgerType;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.out.WalletChargeRepository;
import com.bukang.cash.out.WalletLedgerRepository;
import com.bukang.cash.out.pg.PaymentApprovalResult;
import com.bukang.cash.out.pg.PaymentClient;
import com.bukang.cash.out.pg.PaymentLookupResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 충전 승인 결과를 끝내 확인 못 한 건을 PG 재조회로 맞춘다
 * 대상: UNCERTAIN(재조회까지 실패했던 건), IN_PROGRESS로 너무 오래 멈춘 건(처리 중 서버가 죽는 등으로 끝맺지 못함)
 * CashChargeConfirmUseCase와 같은 이유로 건마다 짧은 트랜잭션을 쓴다 — PG 호출을 트랜잭션 밖에서 하기 위해서다
 * 아직 모르는 건(PG 처리 중, 조회 실패)은 FAILED로 단정하지 않고 다음 실행으로 미룬다.
 * 오래 못 맞춘 건은 error 로그로 남겨 사람이 확인한다(알림 연동은 이후 과제)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CashChargeReconcileUseCase {
	// 연달아 이만큼 조회에 실패하면 PG가 내려간 것으로 보고 이번 실행을 멈춘다. 남은 건마다 응답 제한까지 기다리지 않기 위해서다
	private static final int MAX_CONSECUTIVE_LOOKUP_FAILURES = 3;

	private final ChargeReconcileProperties chargeReconcileProperties;
	private final CashSupport cashSupport;
	private final WalletChargeRepository walletChargeRepository;
	private final WalletLedgerRepository walletLedgerRepository;
	private final PaymentClient paymentClient;
	private final TransactionTemplate transactionTemplate;

	public void reconcile(LocalDateTime now) {
		LocalDateTime staleBefore = now.minusMinutes(chargeReconcileProperties.staleInProgressMinutes());
		LocalDateTime alertBefore = now.minusMinutes(chargeReconcileProperties.alertAfterMinutes());

		List<ChargeTarget> targets = transactionTemplate.execute(status -> collectTargets(staleBefore));

		int consecutiveLookupFailures = 0;
		for (int i = 0; i < targets.size(); i++) {
			boolean pgAnswered = reconcileOne(targets.get(i), alertBefore);
			consecutiveLookupFailures = pgAnswered ? 0 : consecutiveLookupFailures + 1;
			if (consecutiveLookupFailures >= MAX_CONSECUTIVE_LOOKUP_FAILURES) {
				log.warn("충전 대사: PG 조회가 {}번 연달아 실패해 이번 실행을 멈춥니다. 남은 건={}",
					consecutiveLookupFailures, targets.size() - i - 1);
				return;
			}
		}
	}

	// 오래된 건부터 본다. 중간에 멈춰도 같은 건이 계속 뒤로 밀리지 않게 하기 위해서다
	private List<ChargeTarget> collectTargets(LocalDateTime staleBefore) {
		List<WalletCharge> charges = new ArrayList<>(walletChargeRepository.findByStatus(ChargeStatus.UNCERTAIN));
		charges.addAll(walletChargeRepository.findByStatusAndModifyDateBefore(ChargeStatus.IN_PROGRESS, staleBefore));

		return charges.stream()
			.sorted(Comparator.comparing(WalletCharge::getModifyDate))
			.map(charge -> new ChargeTarget(
				charge.getWallet().getId(), charge.getIdempotencyKey(), charge.getModifyDate()))
			.toList();
	}

	// PG가 답했으면 true (결과가 아직 정해지지 않았어도), 조회 자체가 실패했으면 false
	private boolean reconcileOne(ChargeTarget target, LocalDateTime alertBefore) {
		PaymentLookupResult lookup;
		try {
			lookup = paymentClient.lookupByOrderId(target.orderId());
		} catch (PaymentLookupFailedException lookupFailure) {
			logUnresolved(target, alertBefore, "PG 재조회에 실패했습니다");
			return false;
		}
		switch (lookup.status()) {
			case COMPLETED -> transactionTemplate.executeWithoutResult(
				status -> recordCompletedLocked(target.customerId(), target.orderId(), lookup.approval()));
			case NOT_COMPLETED -> transactionTemplate.executeWithoutResult(
				status -> recordFailedLocked(target.customerId(), target.orderId()));
			case PENDING -> logUnresolved(target, alertBefore, "PG에서 아직 승인 결과가 정해지지 않았습니다");
		}
		return true;
	}

	private void logUnresolved(ChargeTarget target, LocalDateTime alertBefore, String reason) {
		if (target.since().isBefore(alertBefore)) {
			log.error("충전 대사: {}분 넘게 결과를 확인하지 못했습니다. 수동 확인 필요. 사유={}, orderId={}",
				chargeReconcileProperties.alertAfterMinutes(), reason, target.orderId());
			return;
		}
		log.warn("충전 대사: {}. 다음 실행으로 미룹니다. orderId={}", reason, target.orderId());
	}

	private void recordCompletedLocked(long customerId, String orderId, PaymentApprovalResult result) {
		Wallet customerWallet = cashSupport.lockWallet(customerId);
		WalletCharge existing = walletChargeRepository.findByIdempotencyKey(orderId).orElse(null);
		if (existing == null || existing.getStatus() == ChargeStatus.COMPLETED) {
			return;
		}
		existing.markCompleted(result.pgPaymentKey(), LocalDateTime.now(), result.approvedAt());
		walletLedgerRepository.save(customerWallet.credit(
			result.approvedAmount(), LedgerType.충전__PG결제_토스페이먼츠, existing));
		log.info("충전 대사: 확인 못 했던 충전을 완료로 맞췄습니다. orderId={}", orderId);
	}

	private void recordFailedLocked(long customerId, String orderId) {
		// 잔액은 안 바꾸지만, 같은 건을 CashChargeConfirmUseCase(확인 요청·대기)와 동시에 건드리지 않도록 지갑을 잠근다
		cashSupport.lockWallet(customerId);
		WalletCharge existing = walletChargeRepository.findByIdempotencyKey(orderId).orElse(null);
		boolean stillOpen = existing != null
			&& (existing.getStatus() == ChargeStatus.IN_PROGRESS || existing.getStatus() == ChargeStatus.UNCERTAIN);
		if (!stillOpen) {
			return;
		}
		existing.markFailed();
		log.info("충전 대사: 확인 못 했던 충전을 실패로 맞췄습니다. orderId={}", orderId);
	}

	// since: UNCERTAIN·IN_PROGRESS가 된 시각. 배치는 결과를 못 맞추면 행을 고치지 않으므로 그대로 유지된다
	private record ChargeTarget(long customerId, String orderId, LocalDateTime since) {
	}
}
