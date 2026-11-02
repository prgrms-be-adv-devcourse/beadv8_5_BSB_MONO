package com.bukang.cash.app;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.cash.config.ChargeProperties;
import com.bukang.cash.domain.charge.CashChargePolicy;
import com.bukang.cash.domain.charge.ChargeStatus;
import com.bukang.cash.domain.charge.WalletCharge;
import com.bukang.cash.domain.exception.ChargeAmountTooSmallException;
import com.bukang.cash.domain.exception.ChargeInProgressException;
import com.bukang.cash.domain.exception.ChargeMemberMismatchException;
import com.bukang.cash.domain.exception.PaymentApprovalFailedException;
import com.bukang.cash.domain.wallet.LedgerType;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.dto.CashChargeResponseDto;
import com.bukang.cash.out.WalletChargeRepository;
import com.bukang.cash.out.WalletLedgerRepository;
import com.bukang.cash.out.pg.PaymentApprovalResult;
import com.bukang.cash.out.pg.PaymentClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CashChargeConfirmUseCase {
	// Toss: "이미 처리가 끝난 결제"
	private static final String ALREADY_PROCESSED_PAYMENT = "ALREADY_PROCESSED_PAYMENT";

	private final ChargeProperties chargeProperties;
	private final CashSupport cashSupport;
	private final WalletChargeRepository walletChargeRepository;
	private final WalletLedgerRepository walletLedgerRepository;
	private final PaymentClient paymentClient;
	private final TransactionTemplate transactionTemplate;

	public CashChargeResponseDto confirmWalletCharge(long customerId, String paymentKey, String orderId, long amount) {
		if (amount < CashChargePolicy.MINIMUM_AMOUNT) {
			throw new ChargeAmountTooSmallException("최소 충전 금액은 10,000원입니다.");
		}

		ChargeClaim claim = transactionTemplate.execute(status -> claimChargeLocked(customerId, orderId, amount));
		if (claim.resolved() != null) {
			return claim.resolved();
		}
		if (claim.alreadyInProgress()) {
			return waitForResolution(customerId, orderId);
		}

		PaymentApprovalResult result;
		try {
			result = paymentClient.approve(paymentKey, orderId, amount);
		} catch (PaymentApprovalFailedException failure) {
			Optional<PaymentApprovalResult> actuallyCompleted = recoverIfAlreadyProcessed(failure, orderId);
			if (actuallyCompleted.isPresent()) {
				PaymentApprovalResult recovered = actuallyCompleted.get();
				return transactionTemplate.execute(
					status -> recordCompletedLocked(customerId, orderId, recovered));
			}
			return transactionTemplate.execute(status -> recordFailedLocked(customerId, orderId));
		}

		try {
			return transactionTemplate.execute(status -> recordCompletedLocked(customerId, orderId, result));
		} catch (RuntimeException e) {
			// Toss 승인은 끝나 돈이 이미 빠진 뒤라, 이 트랜잭션이 DB 문제로 실패하면 기록 없이 돈만 빠진다. 자동 복구 수단이 없으니, 수동으로 맞출 수 있게 단서를 남긴다
			log.error("Toss 승인 후 지갑 반영이 실패했습니다. 수동 확인 필요. orderId={}, paymentKey={}, pgPaymentKey={}",
				orderId, paymentKey, result.pgPaymentKey(), e);
			throw e;
		}
	}

	//지갑 잠금
	private ChargeClaim claimChargeLocked(long customerId, String orderId, long amount) {
		Wallet customerWallet = cashSupport.lockWallet(customerId);
		WalletCharge existing = findMatchingCharge(customerWallet, orderId);
		if (existing != null && existing.getStatus() == ChargeStatus.COMPLETED) {
			return ChargeClaim.resolved(toDto(existing, customerWallet.getBalance()));
		}
		if (existing != null && existing.getStatus() == ChargeStatus.IN_PROGRESS) {
			return ChargeClaim.waiting();
		}
		WalletCharge charge = existing != null
			? existing
			: walletChargeRepository.save(new WalletCharge(customerWallet, amount, "TOSS", orderId));
		charge.markInProgress();
		return ChargeClaim.claimed();
	}

	// IN_PROGRESS일때 Toss호출하지 않고 기다림
	private CashChargeResponseDto waitForResolution(long customerId, String orderId) {
		for (int attempt = 0; attempt < chargeProperties.maxWaitAttempts(); attempt++) {
			sleep(chargeProperties.waitIntervalMillis());
			CashChargeResponseDto resolved = transactionTemplate.execute(
				status -> findResolvedLocked(customerId, orderId));
			if (resolved != null) {
				return resolved;
			}
		}
		// 선점한 요청이 끝날 때까지 기다렸는데도 안 풀렸다 — 그 요청이 멈췄다는 뜻이다 (서버 재시작 등).
		// 자동 재시도는 하지 않고 알린다. 실제 정리는 대사 배치(BUK-8)가 Toss 재조회로 맡는다
		throw new ChargeInProgressException("이미 처리 중인 충전 요청입니다. 잠시 후 다시 확인해 주세요.");
	}

	private CashChargeResponseDto findResolvedLocked(long customerId, String orderId) {
		Wallet customerWallet = cashSupport.lockWallet(customerId);
		WalletCharge existing = findMatchingCharge(customerWallet, orderId);
		if (existing == null || existing.getStatus() == ChargeStatus.IN_PROGRESS) {
			return null;
		}
		return toDto(existing, customerWallet.getBalance());
	}

	// Toss가 "이미 처리된 결제"라고 거절했을 때만, 실제로 승인까지 끝났는지 재조회해서 확인한다
	private Optional<PaymentApprovalResult> recoverIfAlreadyProcessed(
		PaymentApprovalFailedException failure, String orderId
	) {
		if (!ALREADY_PROCESSED_PAYMENT.equals(failure.getPgCode())) {
			return Optional.empty();
		}
		return paymentClient.findCompletedByOrderId(orderId);
	}

	private CashChargeResponseDto recordCompletedLocked(
		long customerId, String orderId, PaymentApprovalResult result
	) {
		Wallet customerWallet = cashSupport.lockWallet(customerId);
		WalletCharge existing = findMatchingCharge(customerWallet, orderId);
		if (existing.getStatus() == ChargeStatus.COMPLETED) {
			return toDto(existing, customerWallet.getBalance());
		}
		existing.markCompleted(result.pgPaymentKey(), LocalDateTime.now());
		walletLedgerRepository.save(customerWallet.credit(
			result.approvedAmount(), LedgerType.충전__PG결제_토스페이먼츠, existing));
		return CashChargeResponseDto.completed(customerWallet.getBalance());
	}

	private CashChargeResponseDto recordFailedLocked(long customerId, String orderId) {
		Wallet customerWallet = cashSupport.lockWallet(customerId);
		WalletCharge existing = findMatchingCharge(customerWallet, orderId);
		if (existing.getStatus() != ChargeStatus.IN_PROGRESS) {
			return toDto(existing, customerWallet.getBalance());
		}
		existing.markFailed();
		return CashChargeResponseDto.failed(customerWallet.getBalance());
	}

	private WalletCharge findMatchingCharge(Wallet customerWallet, String orderId) {
		WalletCharge existing = walletChargeRepository.findByIdempotencyKey(orderId).orElse(null);
		if (existing == null) {
			return null;
		}
		if (existing.getWallet().getId() != customerWallet.getId()) {
			throw new ChargeMemberMismatchException("이 orderId로 충전한 회원과 요청한 회원이 다릅니다.");
		}
		return existing;
	}

	private CashChargeResponseDto toDto(WalletCharge charge, long balance) {
		return charge.getStatus() == ChargeStatus.COMPLETED
			? CashChargeResponseDto.completed(balance)
			: CashChargeResponseDto.failed(balance);
	}

	private void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ChargeInProgressException("충전 결과를 기다리는 중 인터럽트가 발생했습니다.");
		}
	}

	private record ChargeClaim(CashChargeResponseDto resolved, boolean alreadyInProgress) {
		static ChargeClaim resolved(CashChargeResponseDto dto) {
			return new ChargeClaim(dto, false);
		}

		static ChargeClaim waiting() {
			return new ChargeClaim(null, true);
		}

		static ChargeClaim claimed() {
			return new ChargeClaim(null, false);
		}
	}
}
