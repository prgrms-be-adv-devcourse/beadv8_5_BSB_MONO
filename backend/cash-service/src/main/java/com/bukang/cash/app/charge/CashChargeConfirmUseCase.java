package com.bukang.cash.app.charge;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.cash.app.CashSupport;
import com.bukang.cash.config.ChargeConfirmProperties;
import com.bukang.cash.domain.charge.CashChargePolicy;
import com.bukang.cash.domain.charge.ChargeStatus;
import com.bukang.cash.domain.charge.WalletCharge;
import com.bukang.cash.domain.exception.charge.ChargeAmountTooLargeException;
import com.bukang.cash.domain.exception.charge.ChargeAmountTooSmallException;
import com.bukang.cash.domain.exception.charge.ChargeInProgressException;
import com.bukang.cash.domain.exception.charge.ChargeMemberMismatchException;
import com.bukang.cash.domain.exception.charge.PaymentApprovalFailedException;
import com.bukang.cash.domain.exception.charge.PaymentLookupFailedException;
import com.bukang.cash.domain.wallet.LedgerType;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.dto.charge.CashChargeResponseDto;
import com.bukang.cash.out.charge.WalletChargeRepository;
import com.bukang.cash.out.pg.PaymentApprovalResult;
import com.bukang.cash.out.pg.PaymentClient;
import com.bukang.cash.out.pg.PaymentLookupResult;
import com.bukang.cash.out.wallet.WalletLedgerRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CashChargeConfirmUseCase {
	private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

	private final ChargeConfirmProperties chargeConfirmProperties;
	private final CashSupport cashSupport;
	private final WalletChargeRepository walletChargeRepository;
	private final WalletLedgerRepository walletLedgerRepository;
	private final PaymentClient paymentClient;
	private final TransactionTemplate transactionTemplate;
	private final ChargeAbuseGuard chargeAbuseGuard;

	public CashChargeResponseDto confirmWalletCharge(long customerId, String paymentKey, String orderId, long amount) {
		if (amount < CashChargePolicy.MINIMUM_AMOUNT) {
			throw new ChargeAmountTooSmallException("최소 충전 금액은 10,000원입니다.");
		}
		if (amount > CashChargePolicy.MAX_PER_CHARGE) {
			throw new ChargeAmountTooLargeException("1회 충전 한도는 100,000원입니다.");
		}
		chargeAbuseGuard.assertNotCoolingDown(customerId);

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
			chargeAbuseGuard.resetFailureCount(customerId);
		} catch (PaymentApprovalFailedException failure) {
			if (!failure.isResultUnknown()) {
				// PG가 거절을 분명히 알려 줬다 (카드 한도 초과, 일시 장애, 결제 시간 만료 등). 그 문구를 그대로 보여 준다
				chargeAbuseGuard.registerDefiniteFailure(customerId);
				return transactionTemplate.execute(
					status -> recordFailedLocked(customerId, orderId, failure.getMessage()));
			}
			return resolveUnknownApproval(customerId, orderId);
		}

		try {
			return transactionTemplate.execute(status -> recordCompletedLocked(customerId, orderId, result));
		} catch (RuntimeException e) {
			// Toss 승인은 끝나 돈이 이미 빠진 뒤라, 이 트랜잭션이 DB 문제로 실패하면 충전 행이 IN_PROGRESS로 남는다.
			// 대사 배치가 멈춘 건으로 보고 Toss 재조회로 맞추지만, 그 전에 확인할 수 있게 단서를 남긴다
			log.error("Toss 승인 후 지갑 반영이 실패했습니다. 확인 필요. orderId={}, paymentKey={}, pgPaymentKey={}",
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
		// 여기까지 왔으면 진짜 새 시도(또는 실패건 재시도)다. 멱등 재확인은 위에서 이미 반환돼 이 줄까지 안 온다.
		// 쿨다운에 걸리면 여기서 던져 이 트랜잭션 전체가 롤백되므로, 충전 행이 선점된 채로 남지 않는다
		chargeAbuseGuard.registerNewAttempt(customerId);
		assertWithinDailyLimit(customerWallet, amount);
		assertWithinBalanceCap(customerWallet, amount);
		WalletCharge charge = existing != null
			? existing
			: walletChargeRepository.save(new WalletCharge(customerWallet, amount, "TOSS", orderId));
		charge.markInProgress();
		return ChargeClaim.claimed();
	}

	// IN_PROGRESS일때 Toss호출하지 않고 기다림
	private CashChargeResponseDto waitForResolution(long customerId, String orderId) {
		for (int attempt = 0; attempt < chargeConfirmProperties.maxWaitAttempts(); attempt++) {
			sleep(chargeConfirmProperties.waitIntervalMillis());
			CashChargeResponseDto resolved = transactionTemplate.execute(
				status -> findResolvedLocked(customerId, orderId));
			if (resolved != null) {
				return resolved;
			}
		}
		// 선점한 요청이 끝날 때까지 기다렸는데도 안 풀렸다 — 그 요청이 멈췄다는 뜻이다 (서버 재시작 등).
		// 자동 재시도는 하지 않고 알린다. 실제 정리는 대사 배치가 Toss 재조회로 맡는다
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

	// 승인됐는지 모를 때(응답 없음, PG 내부 오류, "처리 중"·"이미 처리됨") 결제 조회로 확인한다.
	// 그래도 모르면 FAILED로 단정하지 않고 UNCERTAIN으로 남겨 대사 배치에 맡긴다
	private CashChargeResponseDto resolveUnknownApproval(long customerId, String orderId) {
		PaymentLookupResult lookup = lookupWithRetry(orderId);
		return switch (lookup.status()) {
			case COMPLETED -> transactionTemplate.execute(
				status -> recordCompletedLocked(customerId, orderId, lookup.approval()));
			case NOT_COMPLETED -> transactionTemplate.execute(
				status -> recordFailedLocked(customerId, orderId, CashChargeResponseDto.DEFAULT_FAILED_MESSAGE));
			case PENDING -> transactionTemplate.execute(status -> recordUncertainLocked(customerId, orderId));
		};
	}

	// 처리 중이거나 조회가 실패하면 정해진 간격만큼 기다렸다가 다시 조회한다. 끝까지 모르면 PENDING을 돌려준다
	private PaymentLookupResult lookupWithRetry(String orderId) {
		List<Long> retryDelays = chargeConfirmProperties.lookupRetryDelaysMillis();
		for (int attempt = 0; ; attempt++) {
			try {
				PaymentLookupResult lookup = paymentClient.lookupByOrderId(orderId);
				if (lookup.status() != PaymentLookupResult.Status.PENDING) {
					return lookup;
				}
			} catch (PaymentLookupFailedException lookupFailure) {
				log.warn("충전 확정: PG 결제 조회에 실패했습니다. attempt={}, orderId={}", attempt + 1, orderId);
			}
			if (attempt >= retryDelays.size()) {
				return PaymentLookupResult.pending();
			}
			sleep(retryDelays.get(attempt));
		}
	}

	private CashChargeResponseDto recordCompletedLocked(
		long customerId, String orderId, PaymentApprovalResult result
	) {
		Wallet customerWallet = cashSupport.lockWallet(customerId);
		WalletCharge existing = findMatchingCharge(customerWallet, orderId);
		if (existing.getStatus() == ChargeStatus.COMPLETED) {
			return toDto(existing, customerWallet.getBalance());
		}
		existing.markCompleted(result.pgPaymentKey(), LocalDateTime.now(), result.approvedAt());
		walletLedgerRepository.save(customerWallet.credit(
			result.approvedAmount(), LedgerType.충전__PG결제_토스페이먼츠, existing));
		return CashChargeResponseDto.completed(customerWallet.getBalance());
	}

	private CashChargeResponseDto recordFailedLocked(long customerId, String orderId, String message) {
		Wallet customerWallet = cashSupport.lockWallet(customerId);
		WalletCharge existing = findMatchingCharge(customerWallet, orderId);
		if (existing.getStatus() != ChargeStatus.IN_PROGRESS) {
			return toDto(existing, customerWallet.getBalance());
		}
		existing.markFailed();
		return CashChargeResponseDto.failed(customerWallet.getBalance(), message);
	}

	// IN_PROGRESS로 멈춰 있었는데, PG 재조회까지 실패해 승인 여부를 끝내 확인 못 했다
	private CashChargeResponseDto recordUncertainLocked(long customerId, String orderId) {
		Wallet customerWallet = cashSupport.lockWallet(customerId);
		WalletCharge existing = findMatchingCharge(customerWallet, orderId);
		if (existing.getStatus() != ChargeStatus.IN_PROGRESS) {
			return toDto(existing, customerWallet.getBalance());
		}
		existing.markUncertain();
		return CashChargeResponseDto.uncertain(customerWallet.getBalance());
	}

	// 자정(KST) 기준 오늘 이미 끝난 충전 합계 + 이번 요청액이 일일 한도를 넘으면 거절한다
	private void assertWithinDailyLimit(Wallet customerWallet, long amount) {
		LocalDateTime startOfToday = LocalDate.now(KOREA).atStartOfDay();
		long todayTotal = walletLedgerRepository.sumAmountSince(
			customerWallet, LedgerType.충전__PG결제_토스페이먼츠, startOfToday);
		if (todayTotal + amount > CashChargePolicy.MAX_PER_DAY) {
			throw new ChargeAmountTooLargeException("일일 충전 한도는 1,000,000원입니다.");
		}
	}

	// 지갑에 평생 쌓인 금액이 아니라 지금 잔액(이번 충전액 더한 값) 기준이라, 환급하면 다시 여유가 생긴다
	private void assertWithinBalanceCap(Wallet customerWallet, long amount) {
		if (customerWallet.getBalance() + amount > CashChargePolicy.MAX_BALANCE) {
			throw new ChargeAmountTooLargeException("보유 한도는 10,000,000원입니다.");
		}
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
		return switch (charge.getStatus()) {
			case COMPLETED -> CashChargeResponseDto.completed(balance);
			case UNCERTAIN -> CashChargeResponseDto.uncertain(balance);
			default -> CashChargeResponseDto.failed(balance);
		};
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
