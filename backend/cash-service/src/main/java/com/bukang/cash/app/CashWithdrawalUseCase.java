package com.bukang.cash.app;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

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
	static final Set<String> CARD_REFUND_IMPOSSIBLE =
		Set.of("EXCEED_MAX_REFUND_DUE", "NOT_ALLOWED_PARTIAL_REFUND");

	private final WalletWithdrawalRepository walletWithdrawalRepository;
	private final WalletWithdrawalItemRepository walletWithdrawalItemRepository;
	private final WalletRepository walletRepository;
	private final WalletLedgerRepository walletLedgerRepository;
	private final WalletChargeRepository walletChargeRepository;
	private final CashRefundableChargeQueryUseCase cashRefundableChargeQueryUseCase;
	private final PaymentClient paymentClient;
	private final TransactionTemplate transactionTemplate;

	// 정책 5장 2차 1: 토스를 부르는 동안 지갑 잠금을 잡지 않도록 세 단계로 나눈다 (충전 확정과 같은 구조).
	// 1) 잠금 트랜잭션: 계획·차감·원장  2) 트랜잭션 밖: 카드 몫마다 토스 결제취소  3) 잠금 트랜잭션: 결과 반영
	public CashWithdrawalResponseDto requestWithdrawal(
		long customerId, String bankCode, String accountNumber, String accountHolder, String idempotencyKey,
		LocalDateTime now
	) {
		Requested requested = transactionTemplate.execute(status -> recordRequestLocked(
			customerId, bankCode, accountNumber, accountHolder, idempotencyKey, now));
		if (requested.replay() != null) {
			return requested.replay();
		}
		List<CardOutcome> outcomes = new ArrayList<>();
		for (CardCall call : requested.calls()) {
			outcomes.add(callPg(call));
		}
		return transactionTemplate.execute(status -> applyOutcomesLocked(customerId, requested, outcomes));
	}

	private Requested recordRequestLocked(
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
			return Requested.replayOf(new CashWithdrawalResponseDto(
				replayed.getAmount(), customerWallet.getBalance(), 0, 0, 0, 0, false));
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
		List<CardCall> calls = new ArrayList<>();
		for (WithdrawalPlan.CardPart part : plan.cardParts()) {
			walletLedgerRepository.save(
				customerWallet.debit(part.amount(), LedgerType.출금__PG결제취소_토스페이먼츠, withdrawal));
			WalletCharge charge = walletChargeRepository.findById(part.chargeId()).orElseThrow();
			String pgIdempotencyKey = idempotencyKey + ":" + part.chargeId();
			// 토스를 부르기 전에 몫을 남겨, 결과를 반영하기 전에 서버가 멈춰도 재확인이 같은 키로 이어받게 한다
			WalletWithdrawalItem item = walletWithdrawalItemRepository.save(new WalletWithdrawalItem(
				withdrawal, charge, part.amount(), WithdrawalItemStatus.PENDING, pgIdempotencyKey));
			calls.add(new CardCall(item.getId(), charge.getId(), part.amount(), charge.getPgPaymentKey(),
				pgIdempotencyKey));
		}
		if (!calls.isEmpty()) {
			withdrawal.markInProgress();
		}
		if (accountAmount > 0) {
			walletLedgerRepository.save(customerWallet.debit(accountAmount, LedgerType.출금__통장입금, withdrawal));
		}
		return new Requested(null, withdrawal.getId(), amount, cardAmount, accountAmount, hasBankAccount, calls);
	}

	private CardOutcome callPg(CardCall call) {
		try {
			return new CardOutcome(call,
				paymentClient.cancel(call.pgPaymentKey(), CANCEL_REASON, call.amount(), call.pgIdempotencyKey()), null);
		} catch (PaymentCancelFailedException failure) {
			return new CardOutcome(call, null, failure);
		}
	}

	private CashWithdrawalResponseDto applyOutcomesLocked(
		long customerId, Requested requested, List<CardOutcome> outcomes
	) {
		Wallet customerWallet = walletRepository.findByIdForUpdate(customerId).orElseThrow();
		WalletWithdrawal withdrawal = walletWithdrawalRepository.findById(requested.withdrawalId()).orElseThrow();
		long revertedAmount = 0;
		long processingAmount = 0;
		long switchedToAccountAmount = 0;
		boolean accountRequired = false;
		for (CardOutcome outcome : outcomes) {
			CardCall call = outcome.call();
			WalletCharge charge = walletChargeRepository.findById(call.chargeId()).orElseThrow();
			WalletWithdrawalItem item = walletWithdrawalItemRepository.findById(call.itemId()).orElseThrow();
			if (outcome.failure() == null) {
				// 요청 금액이 아니라 PG가 알려 준 취소 금액을 기록한다 (승인 때와 같은 원칙)
				charge.recordCancel(outcome.result().canceledAmount());
				item.markCompleted();
				continue;
			}
			PaymentCancelFailedException rejected = outcome.failure();
			if (rejected.isResultUnknown()) {
				// PG가 이미 취소했을 수도 있어 되돌리면 돈이 두 번 나간다. 처리 중으로 두고 재확인으로 맞춘다
				item.markUnknown();
				processingAmount += call.amount();
				continue;
			}
			// PG가 확실히 거절했으니 이 몫은 카드로 돌려주지 못했다. 카드 몫 줄을 되돌린다
			walletLedgerRepository.save(customerWallet.credit(call.amount(),
				LedgerType.출금취소__PG결제취소_토스페이먼츠, LedgerRefType.Withdrawal, withdrawal.getId()));
			if (CARD_REFUND_IMPOSSIBLE.contains(rejected.getPgCode())) {
				if (requested.hasBankAccount()) {
					// 정책: 기한 초과·부분 취소 불가로 카드로 돌려줄 수 없으면 계좌 송금으로 바꾼다
					walletLedgerRepository.save(
						customerWallet.debit(call.amount(), LedgerType.출금__통장입금, withdrawal));
					item.markTransferredToAccount();
					switchedToAccountAmount += call.amount();
					continue;
				}
				// 계좌 정보가 없으면 다른 몫은 이미 처리됐을 수 있어 신청 전체를 되돌리지 않고 이 몫만 되돌린다
				accountRequired = true;
			}
			// 그 밖의 거절은 예치금에 되돌리고 회원이 다시 신청하게 한다
			item.markReverted();
			revertedAmount += call.amount();
		}
		if (processingAmount > 0) {
			withdrawal.markInProgress();
		} else {
			withdrawal.markCompleted(LocalDateTime.now());
		}

		return new CashWithdrawalResponseDto(requested.amount(), customerWallet.getBalance(),
			requested.cardAmount() - revertedAmount - processingAmount - switchedToAccountAmount,
			requested.accountAmount() + switchedToAccountAmount, processingAmount, revertedAmount, accountRequired);
	}

	// 1단계 결과. replay가 있으면 같은 키 재요청이라 2·3단계 없이 그대로 돌려준다
	private record Requested(
		CashWithdrawalResponseDto replay, long withdrawalId, long amount, long cardAmount, long accountAmount,
		boolean hasBankAccount, List<CardCall> calls
	) {
		static Requested replayOf(CashWithdrawalResponseDto replay) {
			return new Requested(replay, 0, 0, 0, 0, false, List.of());
		}
	}

	private record CardCall(long itemId, long chargeId, long amount, String pgPaymentKey, String pgIdempotencyKey) {
	}

	// result와 failure 중 하나만 있다
	private record CardOutcome(CardCall call, PaymentCancelResult result, PaymentCancelFailedException failure) {
	}
}
