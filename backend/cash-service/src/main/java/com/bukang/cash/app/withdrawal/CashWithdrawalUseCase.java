package com.bukang.cash.app.withdrawal;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.cash.app.payment.CashRefundableChargeQueryUseCase;
import com.bukang.cash.domain.charge.WalletCharge;
import com.bukang.cash.domain.exception.wallet.WalletBalanceEmptyException;
import com.bukang.cash.domain.exception.wallet.WalletNotFoundException;
import com.bukang.cash.domain.exception.withdrawal.PaymentCancelFailedException;
import com.bukang.cash.domain.exception.withdrawal.WithdrawalAccountRequiredException;
import com.bukang.cash.domain.exception.withdrawal.WithdrawalInProgressException;
import com.bukang.cash.domain.exception.withdrawal.WithdrawalMemberMismatchException;
import com.bukang.cash.domain.wallet.LedgerRefType;
import com.bukang.cash.domain.wallet.LedgerType;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.domain.withdrawal.WalletWithdrawal;
import com.bukang.cash.domain.withdrawal.WalletWithdrawalItem;
import com.bukang.cash.domain.withdrawal.WithdrawalItemStatus;
import com.bukang.cash.domain.withdrawal.WithdrawalStatus;
import com.bukang.cash.dto.withdrawal.CashWithdrawalResponseDto;
import com.bukang.cash.out.charge.WalletChargeRepository;
import com.bukang.cash.out.pg.PaymentCancelResult;
import com.bukang.cash.out.pg.PaymentClient;
import com.bukang.cash.out.wallet.WalletLedgerRepository;
import com.bukang.cash.out.wallet.WalletRepository;
import com.bukang.cash.out.withdrawal.WalletWithdrawalItemRepository;
import com.bukang.cash.out.withdrawal.WalletWithdrawalRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CashWithdrawalUseCase {
	static final String CANCEL_REASON = "예치금 환급";

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
			return Requested.replayOf(replayOf(existing.get(), customerWallet.getBalance()));
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

	// 정책: 같은 키 재요청은 다시 처리하지 않고 지금 처리 상태를 돌려준다. 몫별 상태에서 금액을 다시 계산한다.
	// shortcut: 계좌 정보가 없어 되돌린 몫인지는 저장하지 않아 accountRequired는 늘 false, 필요해지면 몫에 사유를 남긴다
	private CashWithdrawalResponseDto replayOf(WalletWithdrawal withdrawal, long balance) {
		WithdrawalAmounts amounts =
			WithdrawalAmounts.of(withdrawal, walletWithdrawalItemRepository.findByWithdrawal(withdrawal));
		return new CashWithdrawalResponseDto(withdrawal.getAmount(), balance, amounts.cardAmount(),
			amounts.accountAmount(), amounts.switchedToAccountAmount(), amounts.processingAmount(),
			amounts.revertedAmount(), false);
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
			if (item.getStatus() != WithdrawalItemStatus.PENDING) {
				// 토스를 기다리는 사이 재확인이 먼저 반영했다. 다시 반영하면 돈이 두 번 움직여 결과를 응답에만 담는다
				switch (item.getStatus()) {
					case REVERTED -> revertedAmount += call.amount();
					case TRANSFERRED_TO_ACCOUNT -> switchedToAccountAmount += call.amount();
					case MANUAL_CHECK -> processingAmount += call.amount();
					default -> {
					}
				}
				continue;
			}
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
			CardCancelRejection handling = CardCancelRejection.of(rejected.getPgCode());
			if (handling == CardCancelRejection.MANUAL_CHECK) {
				// 이미 카드로 나갔을 수 있어 되돌리지 않는다. 회원에게는 처리 중으로 보이고 사람이 정리한다
				item.markManualCheck();
				log.error("환급 결제취소: PG가 이미 취소됨류로 거절했습니다. 수동 확인 필요. itemId={}, chargeId={}, code={}",
					item.getId(), charge.getId(), rejected.getPgCode());
				processingAmount += call.amount();
				continue;
			}
			// PG가 확실히 거절했으니 이 몫은 카드로 돌려주지 못했다. 카드 몫 줄을 되돌린다
			walletLedgerRepository.save(customerWallet.credit(call.amount(),
				LedgerType.출금취소__PG결제취소_토스페이먼츠, LedgerRefType.Withdrawal, withdrawal.getId()));
			if (handling == CardCancelRejection.SWITCH_TO_ACCOUNT) {
				if (requested.hasBankAccount()) {
					// 정책: 다시 신청해도 카드로 돌려줄 수 없는 거절이면 계좌 송금으로 바꾼다
					walletLedgerRepository.save(
						customerWallet.debit(call.amount(), LedgerType.출금__통장입금, withdrawal));
					item.markTransferredToAccount();
					switchedToAccountAmount += call.amount();
					continue;
				}
				// 계좌 정보가 없으면 다른 몫은 이미 처리됐을 수 있어 신청 전체를 되돌리지 않고 이 몫만 되돌린다
				accountRequired = true;
			}
			// 잠깐 문제·우리 설정 문제 거절은 예치금에 되돌리고 회원이 다시 신청하게 한다
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
			requested.accountAmount() + switchedToAccountAmount, switchedToAccountAmount, processingAmount,
			revertedAmount, accountRequired);
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
