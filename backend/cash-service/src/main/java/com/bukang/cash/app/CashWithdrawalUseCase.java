package com.bukang.cash.app;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.bukang.cash.domain.charge.WalletCharge;
import com.bukang.cash.domain.exception.PaymentCancelFailedException;
import com.bukang.cash.domain.exception.WalletBalanceEmptyException;
import com.bukang.cash.domain.exception.WalletNotFoundException;
import com.bukang.cash.domain.exception.WithdrawalAccountRequiredException;
import com.bukang.cash.domain.exception.WithdrawalInProgressException;
import com.bukang.cash.domain.exception.WithdrawalMemberMismatchException;
import com.bukang.cash.domain.wallet.LedgerRefType;
import com.bukang.cash.domain.wallet.LedgerType;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.domain.withdrawal.WalletWithdrawal;
import com.bukang.cash.domain.withdrawal.WalletWithdrawalItem;
import com.bukang.cash.domain.withdrawal.WithdrawalItemStatus;
import com.bukang.cash.domain.withdrawal.WithdrawalStatus;
import com.bukang.cash.dto.CashWithdrawalResponseDto;
import com.bukang.cash.out.WalletChargeRepository;
import com.bukang.cash.out.WalletLedgerRepository;
import com.bukang.cash.out.WalletRepository;
import com.bukang.cash.out.WalletWithdrawalItemRepository;
import com.bukang.cash.out.WalletWithdrawalRepository;
import com.bukang.cash.out.pg.PaymentCancelResult;
import com.bukang.cash.out.pg.PaymentClient;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CashWithdrawalUseCase {
	static final String CANCEL_REASON = "예치금 환급";
	// 카드로는 돌려줄 수 없다는 토스 거절 코드 (docs.tosspayments.com/reference/error-codes).
	// 환불 가능 기간이 지남, 부분 취소를 허용하지 않는 결제(일부 체크카드·에스크로). 이 몫은 계좌로 보낸다
	private static final Set<String> CARD_REFUND_IMPOSSIBLE =
		Set.of("EXCEED_MAX_REFUND_DUE", "NOT_ALLOWED_PARTIAL_REFUND");

	private final WalletWithdrawalRepository walletWithdrawalRepository;
	private final WalletWithdrawalItemRepository walletWithdrawalItemRepository;
	private final WalletRepository walletRepository;
	private final WalletLedgerRepository walletLedgerRepository;
	private final WalletChargeRepository walletChargeRepository;
	private final CashRefundableChargeQueryUseCase cashRefundableChargeQueryUseCase;
	private final PaymentClient paymentClient;

	public CashWithdrawalResponseDto requestWithdrawal(
		long customerId, String bankCode, String accountNumber, String accountHolder, String idempotencyKey,
		LocalDateTime now
	) {
		Wallet customerWallet = walletRepository.findByIdForUpdate(customerId)
			.orElseThrow(() -> new WalletNotFoundException("존재하지 않는 지갑입니다."));

		Optional<WalletWithdrawal> existing = walletWithdrawalRepository.findByIdempotencyKey(idempotencyKey);
		if (existing.isPresent()) {
			if (existing.get().getWallet().getId() != customerWallet.getId()) {
				throw new WithdrawalMemberMismatchException("이 Idempotency-Key로 환급한 회원과 요청한 회원이 다릅니다.");
			}
			// shortcut: 같은 키 재요청은 몫별 금액을 아직 저장하지 않아 신청 금액과 잔액만 돌려준다, 몫 표가 생기면 몫에서 계산
			WalletWithdrawal replayed = existing.get();
			return new CashWithdrawalResponseDto(replayed.getAmount(), customerWallet.getBalance(), 0, 0, 0, 0, false);
		}
		if (walletWithdrawalRepository.existsByWalletAndStatus(customerWallet, WithdrawalStatus.IN_PROGRESS)) {
			throw new WithdrawalInProgressException("처리 중인 환급이 있어 끝난 뒤에 다시 신청할 수 있습니다.");
		}

		long amount = customerWallet.getBalance();
		if (amount <= 0) {
			throw new WalletBalanceEmptyException("환급할 잔액이 없습니다.");
		}

		// 남은 금액은 잔액 기준이라 예치금을 빼기 전에 계산한다
		WithdrawalPlan plan = WithdrawalPlan.from(
			cashRefundableChargeQueryUseCase.findRefundableCharges(customerId, now).charges());
		long cardAmount = plan.cardParts().stream().mapToLong(WithdrawalPlan.CardPart::amount).sum();
		// 카드로 돌려줄 수 없는 나머지(기한 지난 몫, 정산 대금처럼 충전으로 들어오지 않은 돈)는 계좌로 보낸다
		long accountAmount = amount - cardAmount;
		boolean hasBankAccount = bankCode != null && accountNumber != null && accountHolder != null;
		if (accountAmount > 0 && !hasBankAccount) {
			throw new WithdrawalAccountRequiredException("계좌로 돌려드릴 금액이 있어 계좌 정보가 필요합니다.");
		}

		WalletWithdrawal withdrawal = walletWithdrawalRepository.save(
			new WalletWithdrawal(customerWallet, amount, bankCode, accountNumber, accountHolder, idempotencyKey));
		for (WithdrawalPlan.CardPart part : plan.cardParts()) {
			walletLedgerRepository.save(
				customerWallet.debit(part.amount(), LedgerType.출금__PG결제취소_토스페이먼츠, withdrawal));
		}
		if (accountAmount > 0) {
			walletLedgerRepository.save(customerWallet.debit(accountAmount, LedgerType.출금__통장입금, withdrawal));
		}

		long revertedAmount = 0;
		long processingAmount = 0;
		long switchedToAccountAmount = 0;
		boolean accountRequired = false;
		for (WithdrawalPlan.CardPart part : plan.cardParts()) {
			WalletCharge charge = walletChargeRepository.findById(part.chargeId()).orElseThrow();
			String pgIdempotencyKey = idempotencyKey + ":" + part.chargeId();
			try {
				PaymentCancelResult result = paymentClient.cancel(charge.getPgPaymentKey(), CANCEL_REASON,
					part.amount(), pgIdempotencyKey);
				// 요청 금액이 아니라 PG가 알려 준 취소 금액을 기록한다 (승인 때와 같은 원칙)
				charge.recordCancel(result.canceledAmount());
			} catch (PaymentCancelFailedException rejected) {
				if (rejected.isResultUnknown()) {
					// PG가 이미 취소했을 수도 있어 되돌리면 돈이 두 번 나간다. 처리 중으로 두고 재확인으로 맞춘다
					walletWithdrawalItemRepository.save(new WalletWithdrawalItem(withdrawal, charge, part.amount(),
						WithdrawalItemStatus.UNKNOWN, pgIdempotencyKey));
					processingAmount += part.amount();
					continue;
				}
				// PG가 확실히 거절했으니 이 몫은 카드로 돌려주지 못했다. 카드 몫 줄을 되돌린다
				walletLedgerRepository.save(customerWallet.credit(part.amount(),
					LedgerType.출금취소__PG결제취소_토스페이먼츠, LedgerRefType.Withdrawal, withdrawal.getId()));
				if (CARD_REFUND_IMPOSSIBLE.contains(rejected.getPgCode())) {
					if (hasBankAccount) {
						// 정책: 기한 초과·부분 취소 불가로 카드로 돌려줄 수 없으면 계좌 송금으로 바꾼다
						walletLedgerRepository.save(
							customerWallet.debit(part.amount(), LedgerType.출금__통장입금, withdrawal));
						switchedToAccountAmount += part.amount();
						continue;
					}
					// 계좌 정보가 없으면 다른 몫은 이미 처리됐을 수 있어 신청 전체를 되돌리지 않고 이 몫만 되돌린다
					accountRequired = true;
				}
				// 그 밖의 거절은 예치금에 되돌리고 회원이 다시 신청하게 한다
				revertedAmount += part.amount();
			}
		}
		if (processingAmount > 0) {
			withdrawal.markInProgress();
		} else {
			withdrawal.markCompleted(LocalDateTime.now());
		}

		return new CashWithdrawalResponseDto(amount, customerWallet.getBalance(),
			cardAmount - revertedAmount - processingAmount - switchedToAccountAmount,
			accountAmount + switchedToAccountAmount, processingAmount, revertedAmount, accountRequired);
	}
}
