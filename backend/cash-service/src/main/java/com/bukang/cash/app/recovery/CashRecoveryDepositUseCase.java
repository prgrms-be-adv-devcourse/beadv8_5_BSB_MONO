package com.bukang.cash.app.recovery;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.cash.app.CashSupport;
import com.bukang.cash.config.CashWalletProperties;
import com.bukang.cash.domain.recovery.RecoveryDeposit;
import com.bukang.cash.domain.wallet.LedgerRefType;
import com.bukang.cash.domain.wallet.LedgerType;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.out.pg.PaymentApprovalResult;
import com.bukang.cash.out.pg.PaymentClient;
import com.bukang.cash.out.pg.PaymentLookupResult;
import com.bukang.cash.out.recovery.RecoveryDepositRepository;
import com.bukang.cash.out.wallet.WalletLedgerRepository;

import lombok.RequiredArgsConstructor;

/**
 * 회수 입금 기록: 판매자가 청구용 가상계좌에 입금했다는 PG 알림을 받아 홀딩 지갑에 회수 금액을 넣는다
 * 알림 내용을 그대로 믿지 않고 PG에 다시 조회해 입금을 확인한다. PG 조회는 트랜잭션 밖에서 하고,
 * 기록은 홀딩 지갑을 잠근 짧은 트랜잭션에서 한다
 */
@Service
@RequiredArgsConstructor
public class CashRecoveryDepositUseCase {
	private final PaymentClient paymentClient;
	private final RecoveryDepositRepository recoveryDepositRepository;
	private final WalletLedgerRepository walletLedgerRepository;
	private final CashSupport cashSupport;
	private final CashWalletProperties cashWalletProperties;
	private final TransactionTemplate transactionTemplate;

	public void receive(String claimCode, String secret) {
		RecoveryDeposit deposit = recoveryDepositRepository.findByClaimCode(claimCode).orElseThrow();
		PaymentLookupResult lookup = paymentClient.lookupByOrderId(claimCode);
		transactionTemplate.executeWithoutResult(status -> record(deposit.getId(), lookup.approval()));
	}

	// 회수금은 플랫폼 밖에서 들어온 돈이라 충전처럼 홀딩 지갑 한 줄만 남긴다
	private void record(long depositId, PaymentApprovalResult approval) {
		Wallet holdingWallet = cashSupport.lockWallet(cashWalletProperties.holdingMemberId());
		RecoveryDeposit deposit = recoveryDepositRepository.findById(depositId).orElseThrow();
		deposit.markDeposited(approval.approvedAt());
		walletLedgerRepository.save(holdingWallet.credit(approval.approvedAmount(),
			LedgerType.회수입금__가상계좌_토스페이먼츠, LedgerRefType.RecoveryDeposit, deposit.getId()));
	}
}
