package com.bukang.cash.app.withdrawal;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.cash.app.CashSupport;
import com.bukang.cash.config.ChargeReconcileProperties;
import com.bukang.cash.domain.exception.withdrawal.PaymentCancelFailedException;
import com.bukang.cash.domain.exception.withdrawal.WithdrawalItemNotFoundException;
import com.bukang.cash.domain.exception.withdrawal.WithdrawalItemNotManualCheckException;
import com.bukang.cash.domain.wallet.LedgerRefType;
import com.bukang.cash.domain.wallet.LedgerType;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.domain.withdrawal.WalletWithdrawal;
import com.bukang.cash.domain.withdrawal.WalletWithdrawalItem;
import com.bukang.cash.domain.withdrawal.WithdrawalItemResolution;
import com.bukang.cash.domain.withdrawal.WithdrawalItemStatus;
import com.bukang.cash.domain.withdrawal.WithdrawalStatus;
import com.bukang.cash.dto.withdrawal.CashWithdrawalItemResolveResponseDto;
import com.bukang.cash.out.pg.PaymentCancelResult;
import com.bukang.cash.out.pg.PaymentClient;
import com.bukang.cash.out.wallet.WalletLedgerRepository;
import com.bukang.cash.out.withdrawal.WalletWithdrawalItemRepository;
import com.bukang.common.global.eventpublisher.EventPublisher;
import com.bukang.common.shared.notification.NotificationKind;
import com.bukang.common.shared.notification.event.NotificationRequestedEvent;

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
	// 재확인이 토스에 다시 물어 맞추는 몫
	private static final List<WithdrawalItemStatus> RECONCILABLE =
		List.of(WithdrawalItemStatus.PENDING, WithdrawalItemStatus.UNKNOWN);
	// 환급 완료 판단: 수동 확인 필요도 아직 정해지지 않은 몫이다
	private static final List<WithdrawalItemStatus> NOT_DONE =
		List.of(WithdrawalItemStatus.PENDING, WithdrawalItemStatus.UNKNOWN, WithdrawalItemStatus.MANUAL_CHECK);
	// 토스 중복 방지 키는 15일 뒤 만료돼 같은 키가 새 요청으로 처리될 수 있다. 그 전에 자동 재요청을 멈춘다
	private static final int MANUAL_CHECK_AFTER_DAYS = 7;
	private static final String COMPLETED_HREF = "/me/deposit";
	private static final int MAX_CONSECUTIVE_NO_ANSWERS = 3;
	// 같은 키가 아직 처리 중이라는 토스 응답(409). 결과는 모르지만 토스가 답했다
	private static final String IDEMPOTENT_REQUEST_PROCESSING = "IDEMPOTENT_REQUEST_PROCESSING";

	private final WalletWithdrawalItemRepository walletWithdrawalItemRepository;
	private final PaymentClient paymentClient;
	private final CashSupport cashSupport;
	private final WalletLedgerRepository walletLedgerRepository;
	private final ChargeReconcileProperties chargeReconcileProperties;
	private final TransactionTemplate transactionTemplate;
	private final EventPublisher eventPublisher;

	// 신청과 같이 토스를 부르는 동안 잠금을 잡지 않는다: 대상 읽기(짧은 트랜잭션) → 몫마다 토스 호출(트랜잭션 밖)
	// → 몫마다 결과 반영(잠금 트랜잭션). 한 몫의 결과는 바로 커밋되고 다음 몫으로 넘어간다
	public void reconcile(LocalDateTime now) {
		List<Target> targets = transactionTemplate.execute(status -> findTargets(now).stream()
			.map(item -> new Target(item.getId(), item.getWithdrawal().getWallet().getId(), item.getCharge().getId(),
				item.getCharge().getPgPaymentKey(), item.getAmount(), item.getPgIdempotencyKey(), item.getCreateDate()))
			.toList());
		int consecutiveNoAnswers = 0;
		for (int i = 0; i < targets.size(); i++) {
			Target target = targets.get(i);
			// 정책 5장 2차 1 「결과를 모를 때」: 한 몫에서 우리 서버 오류가 나도 뒤의 몫은 계속 맞춘다.
			// 토스가 답하지 않은 것이 아니므로 연달아 무응답 횟수에는 세지 않는다
			try {
				if (target.createDate().isBefore(now.minusDays(MANUAL_CHECK_AFTER_DAYS))) {
					transactionTemplate.executeWithoutResult(status -> markManualCheckLocked(target,
						MANUAL_CHECK_AFTER_DAYS + "일 넘게 결제취소 결과를 확인하지 못해 재확인을 멈춥니다."));
					continue;
				}
				Outcome outcome = callPg(target);
				// 충전 재확인처럼 토스가 계속 답하지 않으면(장애) 남은 몫까지 부르지 않고 다음 회차로 미룬다
				consecutiveNoAnswers = outcome.pgAnswered() ? 0 : consecutiveNoAnswers + 1;
				if (consecutiveNoAnswers >= MAX_CONSECUTIVE_NO_ANSWERS) {
					log.warn("환급 재확인: 토스가 {}번 연달아 응답하지 않아 이번 실행을 멈춥니다. 남은 몫={}",
						consecutiveNoAnswers, targets.size() - i - 1);
					return;
				}
				if (outcome.failure() != null && outcome.failure().isResultUnknown()) {
					// 이번에도 모르면 되돌리지 않는다(돈이 두 번 나갈 수 있다). 다음 재확인에 같은 키로 다시 묻는다.
					// 토스 중복 방지 키는 15일만 유효해서, 그보다 훨씬 전에 사람이 보게 한다
					if (target.createDate().isBefore(alertBefore(now))) {
						log.error("환급 재확인: {}분 넘게 결제취소 결과를 확인하지 못했습니다. 수동 확인 필요. itemId={}, chargeId={}",
							chargeReconcileProperties.alertAfterMinutes(), target.itemId(), target.chargeId());
					}
					continue;
				}
				transactionTemplate.executeWithoutResult(status -> applyLocked(target, outcome, now));
			} catch (RuntimeException unexpected) {
				handleUnexpected(target, now, unexpected);
			}
		}
	}

	// 잠깐 생긴 오류는 30분 안에 풀리므로 다음 재확인에 다시 한다. 그보다 오래된 몫이면 사람이 고쳐야 풀리는 오류로 보고 넘긴다
	private void handleUnexpected(Target target, LocalDateTime now, RuntimeException unexpected) {
		if (!target.createDate().isBefore(alertBefore(now))) {
			log.error("환급 재확인: 몫을 처리하다 오류가 나 다음 재확인으로 미룹니다. itemId={}, chargeId={}",
				target.itemId(), target.chargeId(), unexpected);
			return;
		}
		log.error("환급 재확인: {}분 넘은 몫을 처리하다 오류가 났습니다. itemId={}, chargeId={}",
			chargeReconcileProperties.alertAfterMinutes(), target.itemId(), target.chargeId(), unexpected);
		try {
			transactionTemplate.executeWithoutResult(status ->
				markManualCheckLocked(target, "처리하다 오류가 나 재확인을 멈춥니다."));
		} catch (RuntimeException again) {
			log.error("환급 재확인: 수동 확인 필요로 바꾸지 못해 다음 재확인에서 다시 봅니다. itemId={}", target.itemId(), again);
		}
	}

	private LocalDateTime alertBefore(LocalDateTime now) {
		return now.minusMinutes(chargeReconcileProperties.alertAfterMinutes());
	}

	private Outcome callPg(Target target) {
		try {
			return new Outcome(paymentClient.cancel(target.pgPaymentKey(), CashWithdrawalUseCase.CANCEL_REASON,
				target.amount(), target.pgIdempotencyKey()), null);
		} catch (PaymentCancelFailedException failure) {
			return new Outcome(null, failure);
		}
	}

	private void applyLocked(Target target, Outcome outcome, LocalDateTime now) {
		// 신청이나 다른 재확인도 지갑 잠금 안에서 반영하므로, 잠근 뒤 아직 미확정인지 다시 본다.
		// 이미 반영된 몫을 또 반영하면 돈이 두 번 움직인다
		Wallet wallet = cashSupport.lockWallet(target.walletId());
		WalletWithdrawalItem item = walletWithdrawalItemRepository.findById(target.itemId()).orElseThrow();
		if (!RECONCILABLE.contains(item.getStatus())) {
			return;
		}
		if (outcome.failure() == null) {
			item.getCharge().recordCancel(outcome.result().canceledAmount());
			item.markCompleted();
		} else {
			PaymentCancelFailedException rejected = outcome.failure();
			CardCancelRejection handling = CardCancelRejection.of(rejected.getPgCode());
			if (handling == CardCancelRejection.MANUAL_CHECK) {
				// 이미 카드로 나갔을 수 있어 되돌리지 않는다. 사람이 정리할 때까지 환급은 처리 중으로 남는다
				item.markManualCheck();
				log.error("환급 재확인: PG가 이미 취소됨류로 거절했습니다. 수동 확인 필요. itemId={}, chargeId={}, code={}",
					item.getId(), item.getCharge().getId(), rejected.getPgCode());
				return;
			}
			if (handling == CardCancelRejection.SWITCH_TO_ACCOUNT && item.getWithdrawal().hasBankAccount()) {
				// 정책: 다시 신청해도 카드로 돌려줄 수 없는 거절이면 신청 때 받은 계좌로 보낸다
				revert(wallet, item);
				walletLedgerRepository.save(
					wallet.debit(item.getAmount(), LedgerType.출금__통장입금, item.getWithdrawal()));
				item.markTransferredToAccount();
			} else {
				// 잠깐 문제·우리 설정 문제 거절, 또는 계좌로 바꿔야 하는데 계좌 정보가 없으면 예치금에 되돌린다
				revert(wallet, item);
				item.markReverted();
			}
		}
		completeIfDone(item.getWithdrawal(), now);
	}

	// 정책 5장 2차 1 「결과를 모를 때」: 7일이 지나도 모르거나, 30분 넘은 몫에서 오류가 나면 토스에 묻지 않고 사람이 확인하게 한다
	private void markManualCheckLocked(Target target, String reason) {
		cashSupport.lockWallet(target.walletId());
		WalletWithdrawalItem item = walletWithdrawalItemRepository.findById(target.itemId()).orElseThrow();
		if (!RECONCILABLE.contains(item.getStatus())) {
			return;
		}
		item.markManualCheck();
		log.error("환급 재확인: {} 수동 확인 필요. itemId={}, chargeId={}", reason, target.itemId(), target.chargeId());
	}

	/**
	 * 정책 5장 2차 1 「수동 확인 필요」: 담당자가 PG 취소 기록을 보고 내린 결론을 반영한다.
	 * 재확인과 같이 지갑을 잠근 뒤 아직 수동 확인 필요인지 다시 보고 반영한다
	 */
	public CashWithdrawalItemResolveResponseDto resolveManualCheck(
		long itemId, WithdrawalItemResolution resolution, LocalDateTime now
	) {
		long walletId = transactionTemplate.execute(status -> walletWithdrawalItemRepository.findById(itemId)
			.orElseThrow(() -> new WithdrawalItemNotFoundException("존재하지 않는 환급 카드 몫입니다. itemId=" + itemId))
			.getWithdrawal().getWallet().getId());
		return transactionTemplate.execute(status -> {
			Wallet wallet = cashSupport.lockWallet(walletId);
			WalletWithdrawalItem item = walletWithdrawalItemRepository.findById(itemId).orElseThrow();
			if (item.getStatus() != WithdrawalItemStatus.MANUAL_CHECK) {
				throw new WithdrawalItemNotManualCheckException(
					"수동 확인 필요인 몫만 정리할 수 있습니다. itemId=%d, status=%s".formatted(itemId, item.getStatus()));
			}
			if (resolution == WithdrawalItemResolution.CARD_CANCELED) {
				item.getCharge().recordCancel(item.getAmount());
				item.markCompleted();
			} else {
				revert(wallet, item);
				item.markReverted();
			}
			// 사람이 내린 결론으로 돈이 움직여, 나중에 어떤 결론으로 정리했는지 확인할 수 있게 남긴다
			log.warn("환급 몫 수동 정리: itemId={}, result={}, withdrawalId={}, amount={}",
				itemId, resolution, item.getWithdrawal().getId(), item.getAmount());
			completeIfDone(item.getWithdrawal(), now);
			return new CashWithdrawalItemResolveResponseDto(item.getId(), item.getStatus(),
				item.getWithdrawal().getId(), item.getWithdrawal().getStatus());
		});
	}

	// 카드로 돌려주지 못한 몫이다. 신청 때 뺀 몫을 예치금에 되돌린다
	private void revert(Wallet wallet, WalletWithdrawalItem item) {
		walletLedgerRepository.save(wallet.credit(item.getAmount(),
			LedgerType.출금취소__PG결제취소_토스페이먼츠, LedgerRefType.Withdrawal, item.getWithdrawal().getId()));
	}

	// 마지막 몫이 정해지면 환급을 끝내고 알린다. 여기까지 온 환급은 신청 때 끝나지 않고 처리 중이던 환급이다
	private void completeIfDone(WalletWithdrawal withdrawal, LocalDateTime now) {
		if (walletWithdrawalItemRepository.existsByWithdrawalAndStatusIn(withdrawal, NOT_DONE)) {
			return;
		}
		WithdrawalAmounts amounts =
			WithdrawalAmounts.of(withdrawal, walletWithdrawalItemRepository.findByWithdrawal(withdrawal));
		withdrawal.finish(amounts.cardAmount() + amounts.accountAmount(), now);
		eventPublisher.publish(finishedNotice(withdrawal, amounts, now));
	}

	// 정책 5장 2차 1 「알림」: 0원인 금액은 빼고, 계좌로 바꾼 몫은 이유를 함께 적는다. 실패면 실패 문구로 보낸다
	private NotificationRequestedEvent finishedNotice(
		WalletWithdrawal withdrawal, WithdrawalAmounts amounts, LocalDateTime now
	) {
		// 재확인이 여러 번 돌거나 서버가 여러 대여도 알림 서비스가 한 번만 저장하도록(message_id 유니크) 환급 ID로 정한다
		String key = "withdrawal:" + withdrawal.getId() + ":completed";
		String eventId = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
		if (withdrawal.getStatus() == WithdrawalStatus.FAILED) {
			return new NotificationRequestedEvent(eventId, withdrawal.getWallet().getId(),
				NotificationKind.WITHDRAWAL_COMPLETED, "예치금 환급이 되지 않았어요",
				String.format(Locale.KOREA, "카드 취소가 되지 않아 %,d원을 예치금으로 돌려드렸어요. 다시 신청해 주세요.",
					amounts.revertedAmount()), COMPLETED_HREF, now);
		}
		List<String> parts = new ArrayList<>();
		if (amounts.cardAmount() > 0) {
			parts.add(String.format(Locale.KOREA, "카드 취소 요청 %,d원", amounts.cardAmount()));
		}
		if (amounts.accountAmount() > 0) {
			String reason = "";
			if (amounts.switchedToAccountAmount() == amounts.accountAmount()) {
				reason = "(카드 취소가 되지 않아 계좌로 보냄)";
			} else if (amounts.switchedToAccountAmount() > 0) {
				reason = String.format(Locale.KOREA, "(그중 %,d원은 카드 취소가 되지 않아 계좌로 보냄)",
					amounts.switchedToAccountAmount());
			}
			parts.add(String.format(Locale.KOREA, "계좌 송금 %,d원", amounts.accountAmount()) + reason);
		}
		if (amounts.revertedAmount() > 0) {
			parts.add(String.format(Locale.KOREA, "예치금으로 돌아온 금액 %,d원", amounts.revertedAmount()));
		}
		return new NotificationRequestedEvent(eventId, withdrawal.getWallet().getId(),
			NotificationKind.WITHDRAWAL_COMPLETED, "예치금 환급이 끝났어요", String.join(", ", parts), COMPLETED_HREF, now);
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
		long itemId, long walletId, long chargeId, String pgPaymentKey, long amount, String pgIdempotencyKey,
		LocalDateTime createDate
	) {
	}

	// result와 failure 중 하나만 있다
	private record Outcome(PaymentCancelResult result, PaymentCancelFailedException failure) {
		// 연결 실패·타임아웃·5xx·빈 응답만 토스가 답하지 않은 것으로 본다
		boolean pgAnswered() {
			return failure == null || !failure.isResultUnknown()
				|| IDEMPOTENT_REQUEST_PROCESSING.equals(failure.getPgCode());
		}
	}
}
