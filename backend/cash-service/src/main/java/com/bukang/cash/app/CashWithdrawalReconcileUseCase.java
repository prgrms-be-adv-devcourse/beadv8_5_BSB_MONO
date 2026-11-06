package com.bukang.cash.app;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.cash.config.ChargeReconcileProperties;
import com.bukang.cash.domain.exception.PaymentCancelFailedException;
import com.bukang.cash.domain.wallet.LedgerRefType;
import com.bukang.cash.domain.wallet.LedgerType;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.domain.withdrawal.WalletWithdrawalItem;
import com.bukang.cash.domain.withdrawal.WithdrawalItemStatus;
import com.bukang.cash.out.WalletLedgerRepository;
import com.bukang.cash.out.WalletWithdrawalItemRepository;
import com.bukang.cash.out.pg.PaymentCancelResult;
import com.bukang.cash.out.pg.PaymentClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 환급 결제취소 결과를 모르는 카드 몫을 맞춘다. 정책 5장 2차 1 「PG 결제취소와 기한 › 결과를 모를 때」:
 * 신청 때와 같은 중복 방지 키로 결제취소를 다시 요청하면 PG는 한 번만 취소하고 그 결과를 돌려준다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CashWithdrawalReconcileUseCase {
	private static final List<WithdrawalItemStatus> UNFINISHED =
		List.of(WithdrawalItemStatus.PENDING, WithdrawalItemStatus.UNKNOWN);

	private final WalletWithdrawalItemRepository walletWithdrawalItemRepository;
	private final PaymentClient paymentClient;
	private final CashSupport cashSupport;
	private final WalletLedgerRepository walletLedgerRepository;
	private final ChargeReconcileProperties chargeReconcileProperties;
	private final TransactionTemplate transactionTemplate;

	// 신청과 같이 토스를 부르는 동안 잠금을 잡지 않는다: 대상 읽기(짧은 트랜잭션) → 몫마다 토스 호출(트랜잭션 밖)
	// → 몫마다 결과 반영(잠금 트랜잭션). 한 몫의 결과는 바로 커밋되고 다음 몫으로 넘어간다
	public void reconcile(LocalDateTime now) {
		List<Target> targets = transactionTemplate.execute(status -> findTargets(now).stream()
			.map(item -> new Target(item.getId(), item.getCharge().getId(), item.getCharge().getPgPaymentKey(),
				item.getAmount(), item.getPgIdempotencyKey(), item.getCreateDate()))
			.toList());
		for (Target target : targets) {
			Outcome outcome = callPg(target);
			if (outcome.failure() != null && outcome.failure().isResultUnknown()) {
				// 이번에도 모르면 되돌리지 않는다(돈이 두 번 나갈 수 있다). 다음 재확인에 같은 키로 다시 묻는다.
				// 토스 중복 방지 키는 15일만 유효해서, 그보다 훨씬 전에 사람이 보게 한다
				LocalDateTime alertBefore = now.minusMinutes(chargeReconcileProperties.alertAfterMinutes());
				if (target.createDate().isBefore(alertBefore)) {
					log.error("환급 재확인: {}분 넘게 결제취소 결과를 확인하지 못했습니다. 수동 확인 필요. itemId={}, chargeId={}",
						chargeReconcileProperties.alertAfterMinutes(), target.itemId(), target.chargeId());
				}
				continue;
			}
			transactionTemplate.executeWithoutResult(status -> applyLocked(target.itemId(), outcome, now));
		}
	}

	private Outcome callPg(Target target) {
		try {
			return new Outcome(paymentClient.cancel(target.pgPaymentKey(), CashWithdrawalUseCase.CANCEL_REASON,
				target.amount(), target.pgIdempotencyKey()), null);
		} catch (PaymentCancelFailedException failure) {
			return new Outcome(null, failure);
		}
	}

	private void applyLocked(long itemId, Outcome outcome, LocalDateTime now) {
		WalletWithdrawalItem item = walletWithdrawalItemRepository.findById(itemId).orElseThrow();
		if (outcome.failure() == null) {
			item.getCharge().recordCancel(outcome.result().canceledAmount());
			item.markCompleted();
		} else {
			PaymentCancelFailedException rejected = outcome.failure();
			// PG가 확실히 거절했으니 카드로 돌려주지 못했다. 신청 때 뺀 몫을 예치금에 되돌린다
			Wallet wallet = cashSupport.lockWallet(item.getWithdrawal().getWallet().getId());
			walletLedgerRepository.save(wallet.credit(item.getAmount(),
				LedgerType.출금취소__PG결제취소_토스페이먼츠, LedgerRefType.Withdrawal, item.getWithdrawal().getId()));
			if (CashWithdrawalUseCase.CARD_REFUND_IMPOSSIBLE.contains(rejected.getPgCode())
				&& item.getWithdrawal().hasBankAccount()) {
				// 정책: 카드로 돌려줄 수 없는 거절이면 신청 때 받은 계좌로 보낸다
				walletLedgerRepository.save(
					wallet.debit(item.getAmount(), LedgerType.출금__통장입금, item.getWithdrawal()));
				item.markTransferredToAccount();
			} else {
				item.markReverted();
			}
		}
		if (!walletWithdrawalItemRepository.existsByWithdrawalAndStatusIn(item.getWithdrawal(), UNFINISHED)) {
			item.getWithdrawal().markCompleted(now);
		}
	}

	// 결과 모름인 몫 + 서버가 멈춰 PENDING으로 남은 몫. 아직 처리 중인 정상 신청을 건드리지 않도록
	// PENDING은 충전 재확인의 stale 기준(한 번의 처리가 걸릴 수 있는 가장 긴 시간)보다 오래된 것만 고른다
	private List<WalletWithdrawalItem> findTargets(LocalDateTime now) {
		List<WalletWithdrawalItem> targets =
			new ArrayList<>(walletWithdrawalItemRepository.findByStatus(WithdrawalItemStatus.UNKNOWN));
		targets.addAll(walletWithdrawalItemRepository.findByStatusAndCreateDateBefore(
			WithdrawalItemStatus.PENDING, now.minusMinutes(chargeReconcileProperties.staleInProgressMinutes())));
		// 충전 재확인처럼 오래된 몫부터: 회차가 중간에 멈춰도 가장 오래 기다린 몫이 먼저 맞춰진다
		targets.sort(Comparator.comparing(WalletWithdrawalItem::getCreateDate)
			.thenComparingLong(WalletWithdrawalItem::getId));
		return targets;
	}

	private record Target(
		long itemId, long chargeId, String pgPaymentKey, long amount, String pgIdempotencyKey, LocalDateTime createDate
	) {
	}

	// result와 failure 중 하나만 있다
	private record Outcome(PaymentCancelResult result, PaymentCancelFailedException failure) {
	}
}
