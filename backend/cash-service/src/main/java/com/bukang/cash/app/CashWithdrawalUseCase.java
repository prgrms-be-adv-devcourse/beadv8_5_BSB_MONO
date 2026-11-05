package com.bukang.cash.app;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.bukang.cash.domain.charge.WalletCharge;
import com.bukang.cash.domain.exception.WalletBalanceEmptyException;
import com.bukang.cash.domain.exception.WalletNotFoundException;
import com.bukang.cash.domain.exception.WithdrawalAccountRequiredException;
import com.bukang.cash.domain.exception.WithdrawalInProgressException;
import com.bukang.cash.domain.exception.WithdrawalMemberMismatchException;
import com.bukang.cash.domain.wallet.LedgerType;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.domain.withdrawal.WalletWithdrawal;
import com.bukang.cash.domain.withdrawal.WithdrawalStatus;
import com.bukang.cash.dto.CashWithdrawalResponseDto;
import com.bukang.cash.out.WalletChargeRepository;
import com.bukang.cash.out.WalletLedgerRepository;
import com.bukang.cash.out.WalletRepository;
import com.bukang.cash.out.WalletWithdrawalRepository;
import com.bukang.cash.out.pg.PaymentClient;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CashWithdrawalUseCase {
	private static final String CANCEL_REASON = "예치금 환급";

	private final WalletWithdrawalRepository walletWithdrawalRepository;
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
			return new CashWithdrawalResponseDto(replayed.getAmount(), customerWallet.getBalance(), 0, 0, 0, 0);
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
		if (accountAmount > 0 && (bankCode == null || accountNumber == null || accountHolder == null)) {
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

		for (WithdrawalPlan.CardPart part : plan.cardParts()) {
			WalletCharge charge = walletChargeRepository.findById(part.chargeId()).orElseThrow();
			paymentClient.cancel(charge.getPgPaymentKey(), CANCEL_REASON, part.amount(),
				idempotencyKey + ":" + part.chargeId());
		}
		withdrawal.markCompleted(LocalDateTime.now());

		return new CashWithdrawalResponseDto(amount, customerWallet.getBalance(), cardAmount, accountAmount, 0, 0);
	}
}
