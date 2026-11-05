package com.bukang.cash.app;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
	private final WalletWithdrawalItemRepository walletWithdrawalItemRepository;
	private final PaymentClient paymentClient;
	private final CashSupport cashSupport;
	private final WalletLedgerRepository walletLedgerRepository;
	private final ChargeReconcileProperties chargeReconcileProperties;

	@Transactional
	public void reconcile(LocalDateTime now) {
		for (WalletWithdrawalItem item : walletWithdrawalItemRepository.findByStatus(WithdrawalItemStatus.UNKNOWN)) {
			try {
				PaymentCancelResult result = paymentClient.cancel(item.getCharge().getPgPaymentKey(),
					CashWithdrawalUseCase.CANCEL_REASON, item.getAmount(), item.getPgIdempotencyKey());
				item.getCharge().recordCancel(result.canceledAmount());
				item.markCompleted();
			} catch (PaymentCancelFailedException rejected) {
				if (rejected.isResultUnknown()) {
					// 이번에도 모르면 되돌리지 않는다(돈이 두 번 나갈 수 있다). 다음 재확인에 같은 키로 다시 묻는다.
					// 토스 중복 방지 키는 15일만 유효해서, 그보다 훨씬 전에 사람이 보게 한다
					LocalDateTime alertBefore = now.minusMinutes(chargeReconcileProperties.alertAfterMinutes());
					if (item.getCreateDate().isBefore(alertBefore)) {
						log.error("환급 재확인: {}분 넘게 결제취소 결과를 확인하지 못했습니다. 수동 확인 필요. itemId={}, chargeId={}",
							chargeReconcileProperties.alertAfterMinutes(), item.getId(), item.getCharge().getId());
					}
					continue;
				}
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
			if (!walletWithdrawalItemRepository.existsByWithdrawalAndStatus(
				item.getWithdrawal(), WithdrawalItemStatus.UNKNOWN)) {
				item.getWithdrawal().markCompleted(now);
			}
		}
	}
}
