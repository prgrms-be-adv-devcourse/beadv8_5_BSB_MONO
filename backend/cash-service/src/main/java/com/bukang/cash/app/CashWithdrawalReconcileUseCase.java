package com.bukang.cash.app;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

/**
 * 환급 결제취소 결과를 모르는 카드 몫을 맞춘다. 정책 5장 2차 1 「PG 결제취소와 기한 › 결과를 모를 때」:
 * 신청 때와 같은 중복 방지 키로 결제취소를 다시 요청하면 PG는 한 번만 취소하고 그 결과를 돌려준다
 */
@Service
@RequiredArgsConstructor
public class CashWithdrawalReconcileUseCase {
	private final WalletWithdrawalItemRepository walletWithdrawalItemRepository;
	private final PaymentClient paymentClient;
	private final CashSupport cashSupport;
	private final WalletLedgerRepository walletLedgerRepository;

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
					// 이번에도 모르면 되돌리지 않는다(돈이 두 번 나갈 수 있다). 다음 재확인에 같은 키로 다시 묻는다
					continue;
				}
				// PG가 확실히 거절했으니 카드로 돌려주지 못했다. 신청 때 뺀 몫을 예치금에 되돌린다
				Wallet wallet = cashSupport.lockWallet(item.getWithdrawal().getWallet().getId());
				walletLedgerRepository.save(wallet.credit(item.getAmount(),
					LedgerType.출금취소__PG결제취소_토스페이먼츠, LedgerRefType.Withdrawal, item.getWithdrawal().getId()));
				item.markReverted();
			}
			if (!walletWithdrawalItemRepository.existsByWithdrawalAndStatus(
				item.getWithdrawal(), WithdrawalItemStatus.UNKNOWN)) {
				item.getWithdrawal().markCompleted(now);
			}
		}
	}
}
