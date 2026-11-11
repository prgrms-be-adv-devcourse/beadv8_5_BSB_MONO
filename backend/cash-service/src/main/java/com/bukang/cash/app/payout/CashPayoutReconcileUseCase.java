package com.bukang.cash.app.payout;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.cash.app.CashSupport;
import com.bukang.cash.config.CashWalletProperties;
import com.bukang.cash.domain.payout.PayoutTransfer;
import com.bukang.cash.domain.payout.PayoutTransferStatus;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.out.bank.BankTransferClient;
import com.bukang.cash.out.bank.BankTransferResult;
import com.bukang.cash.out.payout.PayoutTransferRepository;
import com.bukang.cash.out.wallet.WalletLedgerRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 결과를 모르는 정산금 송금을 은행에 조회해 완료나 실패로 맞춘다. 실패면 홀딩 지갑에서 뺀 대금을 되돌린다
 * 은행을 부르다 서버가 멈춰 요청함으로 남은 송금도 맞춘다. 은행에 송금이 없다고 나오면, 요청이 아직 은행에
 * 닿는 중일 수 있어 마지막으로 바뀐 지 10분이 지난 송금만 실패로 확정한다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CashPayoutReconcileUseCase {
	private static final List<PayoutTransferStatus> UNFINISHED =
		List.of(PayoutTransferStatus.REQUESTED, PayoutTransferStatus.UNKNOWN);
	private static final int NOT_FOUND_FAIL_AFTER_MINUTES = 10;

	private final PayoutTransferRepository payoutTransferRepository;
	private final BankTransferClient bankTransferClient;
	private final CashSupport cashSupport;
	private final CashWalletProperties cashWalletProperties;
	private final WalletLedgerRepository walletLedgerRepository;
	private final TransactionTemplate transactionTemplate;

	// 지급 요청과 같이 은행을 부르는 동안 잠금을 잡지 않는다: 대상 읽기 → 송금마다 은행 조회(트랜잭션 밖) → 결과 반영(잠금)
	public void reconcile(LocalDateTime now) {
		List<Target> targets = transactionTemplate.execute(status ->
			payoutTransferRepository.findByStatusIn(UNFINISHED).stream()
				.map(transfer -> new Target(transfer.getId(), transfer.bankTransferId(), transfer.getModifyDate()))
				.toList());
		for (Target target : targets) {
			BankTransferResult result = bankTransferClient.lookup(target.bankTransferId());
			// 수정 시각은 은행에 보낸 시각보다 늦거나 같아, 10분 기준을 넉넉하게 지킨다
			boolean notFoundLongEnough = result == BankTransferResult.NOT_FOUND
				&& target.modifyDate().isBefore(now.minusMinutes(NOT_FOUND_FAIL_AFTER_MINUTES));
			if (result == BankTransferResult.COMPLETED || result == BankTransferResult.FAILED || notFoundLongEnough) {
				transactionTemplate.executeWithoutResult(status -> applyLocked(target, result));
			} else {
				log.warn("정산금 송금 재확인: 아직 결과를 모릅니다. transferId={}, 은행 응답={}", target.bankTransferId(), result);
			}
		}
	}

	private void applyLocked(Target target, BankTransferResult result) {
		// 지급 요청도 홀딩 지갑 잠금 안에서 반영하므로, 잠근 뒤 같은 시도가 아직 미확정인지 다시 본다.
		// 이미 반영된 송금을 또 반영하면 되돌리기가 두 번 일어난다
		Wallet holdingWallet = cashSupport.lockWallet(cashWalletProperties.holdingMemberId());
		PayoutTransfer transfer = payoutTransferRepository.findById(target.transferId()).orElseThrow();
		if (!UNFINISHED.contains(transfer.getStatus()) || !transfer.bankTransferId().equals(target.bankTransferId())) {
			return;
		}
		if (result == BankTransferResult.COMPLETED) {
			transfer.markCompleted();
		} else {
			walletLedgerRepository.save(transfer.fail(holdingWallet));
		}
	}

	private record Target(long transferId, String bankTransferId, LocalDateTime modifyDate) {
	}
}
