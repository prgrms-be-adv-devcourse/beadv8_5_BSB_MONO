package com.bukang.cash.app.recovery;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.cash.app.CashSupport;
import com.bukang.cash.config.CashWalletProperties;
import com.bukang.cash.domain.recovery.RecoveryDeposit;
import com.bukang.cash.domain.recovery.RecoveryDepositStatus;
import com.bukang.cash.domain.wallet.LedgerRefType;
import com.bukang.cash.domain.wallet.LedgerType;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.out.pg.PaymentApprovalResult;
import com.bukang.cash.out.pg.PaymentClient;
import com.bukang.cash.out.pg.PaymentLookupResult;
import com.bukang.cash.out.recovery.RecoveryDepositRepository;
import com.bukang.cash.out.wallet.WalletLedgerRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 회수 입금 기록: 판매자가 청구용 가상계좌에 입금했다는 PG 알림을 받아 홀딩 지갑에 회수 금액을 넣는다
 * 알림 내용을 그대로 믿지 않고 PG에 다시 조회해 입금을 확인한다. PG 조회는 트랜잭션 밖에서 하고,
 * 기록은 홀딩 지갑을 잠근 짧은 트랜잭션에서 한다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CashRecoveryDepositUseCase {
	// PG가 입금 완료를 알릴 때의 결제 상태
	private static final String DONE = "DONE";

	private final PaymentClient paymentClient;
	private final RecoveryDepositRepository recoveryDepositRepository;
	private final WalletLedgerRepository walletLedgerRepository;
	private final CashSupport cashSupport;
	private final CashWalletProperties cashWalletProperties;
	private final TransactionTemplate transactionTemplate;

	// pgStatus는 알림 본문의 결제 상태다. 입금 오류를 알아채는 데만 쓰고, 입금 완료와 금액은 PG 조회로 판단한다
	public void receive(String claimCode, String secret, String pgStatus) {
		// 모르는 주문번호도 오류로 답하지 않는다. 오류로 답하면 PG가 같은 알림을 계속 다시 보낸다
		RecoveryDeposit deposit = recoveryDepositRepository.findByClaimCode(claimCode).orElse(null);
		if (deposit == null) {
			log.warn("회수 입금 기록에 없는 주문번호의 입금 알림이라 무시합니다. claimCode={}", claimCode);
			return;
		}
		if (!matchesSecret(deposit, secret)) {
			log.warn("가상계좌 입금 알림의 비밀값이 맞지 않아 무시합니다. claimCode={}", claimCode);
			return;
		}
		// PG는 같은 알림을 여러 번 보낼 수 있다. 이미 기록한 청구면 PG를 다시 조회하지 않는다
		if (deposit.getStatus() != RecoveryDepositStatus.WAITING) {
			warnIfDepositReverted(deposit, pgStatus);
			log.info("이미 처리한 회수 입금이라 알림을 건너뜁니다. claimCode={}, status={}", claimCode, deposit.getStatus());
			return;
		}
		PaymentLookupResult lookup = paymentClient.lookupByOrderId(claimCode);
		if (lookup.status() != PaymentLookupResult.Status.COMPLETED) {
			log.info("PG 조회 결과가 아직 입금 완료가 아니라 기록하지 않습니다. claimCode={}, status={}",
				claimCode, lookup.status());
			return;
		}
		// 다른 금액은 은행·PG가 이체 단계에서 막는다. 그래도 다르면 어느 쪽이 맞는지 모르므로 기록하지 않고 사람이 맞춘다
		long depositedAmount = lookup.approval().approvedAmount();
		if (depositedAmount != deposit.getAmount()) {
			log.error("입금 금액이 청구 금액과 달라 기록하지 않습니다. 사람이 확인해야 합니다. claimCode={}, 청구 금액={}, 입금 금액={}",
				claimCode, deposit.getAmount(), depositedAmount);
			return;
		}
		transactionTemplate.executeWithoutResult(status -> record(deposit.getId(), lookup.approval()));
	}

	// 입금 오류(판매자 계좌 송금 한도 초과, 네트워크 문제 등)면 PG가 상태를 DONE에서 WAITING_FOR_DEPOSIT(옛 버전은 CANCELED)으로
	// 되돌리고 알림을 다시 보낸다. 홀딩 지갑에는 받지 못한 돈이 남고 정산은 이미 회수를 끝냈으므로, 자동으로 되돌리지 않고 사람이 맞춘다
	private void warnIfDepositReverted(RecoveryDeposit deposit, String pgStatus) {
		if (deposit.getStatus() == RecoveryDepositStatus.DEPOSITED && pgStatus != null && !DONE.equals(pgStatus)) {
			log.error("회수 입금 입금 오류: 입금 완료로 기록한 청구의 PG 상태가 {}(으)로 바뀌었습니다. 판매자에게 다시 입금을 안내하고 "
					+ "홀딩 지갑 장부와 정산 청구를 사람이 맞춰야 합니다. claimCode={}, 금액={}",
				pgStatus, deposit.getClaimCode(), deposit.getAmount());
		}
	}

	// 웹훅 주소는 토큰 없이 열려 있어 누구나 부를 수 있다. 발급 때 PG가 준 비밀값과 같아야 PG가 보낸 알림으로 본다
	// 비교 시간으로 값을 알아내지 못하게 길이와 상관없이 끝까지 비교한다
	private boolean matchesSecret(RecoveryDeposit deposit, String secret) {
		return secret != null && MessageDigest.isEqual(
			deposit.getSecret().getBytes(StandardCharsets.UTF_8), secret.getBytes(StandardCharsets.UTF_8));
	}

	// 회수금은 플랫폼 밖에서 들어온 돈이라 충전처럼 홀딩 지갑 한 줄만 남긴다
	// 같은 알림이 동시에 오면 둘 다 위의 입금 대기 확인을 지날 수 있다. 행을 잠근 뒤 다시 확인해 한쪽만 기록한다
	private void record(long depositId, PaymentApprovalResult approval) {
		RecoveryDeposit deposit = recoveryDepositRepository.findByIdForUpdate(depositId).orElseThrow();
		if (deposit.getStatus() != RecoveryDepositStatus.WAITING) {
			return;
		}
		Wallet holdingWallet = cashSupport.lockWallet(cashWalletProperties.holdingMemberId());
		deposit.markDeposited(approval.approvedAt());
		walletLedgerRepository.save(holdingWallet.credit(approval.approvedAmount(),
			LedgerType.회수입금__가상계좌_토스페이먼츠, LedgerRefType.RecoveryDeposit, deposit.getId()));
	}
}
