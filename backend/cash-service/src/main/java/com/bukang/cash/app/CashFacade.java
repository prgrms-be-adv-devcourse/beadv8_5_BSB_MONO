package com.bukang.cash.app;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.cash.app.charge.CashChargeConfirmUseCase;
import com.bukang.cash.app.member.CashSyncMemberUseCase;
import com.bukang.cash.app.member.CashSyncSellerUseCase;
import com.bukang.cash.app.payment.CashCancelOrderPaymentUseCase;
import com.bukang.cash.app.payment.CashCompleteOrderPaymentUseCase;
import com.bukang.cash.app.payment.CashRefundPaymentUseCase;
import com.bukang.cash.app.payment.CashRefundableChargeQueryUseCase;
import com.bukang.cash.app.payout.CashPayoutUseCase;
import com.bukang.cash.app.recovery.CashRecoveryDepositUseCase;
import com.bukang.cash.app.recovery.CashRecoveryVirtualAccountUseCase;
import com.bukang.cash.app.wallet.CashEnsureWalletUseCase;
import com.bukang.cash.app.wallet.CashWalletLedgerQueryUseCase;
import com.bukang.cash.app.withdrawal.CashWithdrawalUseCase;
import com.bukang.cash.domain.member.CashMember;
import com.bukang.cash.domain.payout.PayoutStage;
import com.bukang.cash.domain.seller.CashSeller;
import com.bukang.cash.domain.wallet.LedgerCategory;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.dto.PageDto;
import com.bukang.cash.dto.charge.CashChargeResponseDto;
import com.bukang.cash.dto.payment.CashPaymentResponseDto;
import com.bukang.cash.dto.payment.CashRefundResponseDto;
import com.bukang.cash.dto.payment.RefundableChargesResponseDto;
import com.bukang.cash.dto.payout.CashPayoutResponseDto;
import com.bukang.cash.dto.recovery.CashClaimVirtualAccountResponseDto;
import com.bukang.cash.dto.wallet.CashBalanceResponseDto;
import com.bukang.cash.dto.wallet.WalletBalanceResponseDto;
import com.bukang.cash.dto.wallet.WalletLedgerItemDto;
import com.bukang.cash.dto.withdrawal.CashWithdrawalResponseDto;
import com.bukang.common.shared.seller.event.SellerChangedEvent;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CashFacade {
	private final CashSupport cashSupport;
	private final CashSyncMemberUseCase cashSyncMemberUseCase;
	private final CashEnsureWalletUseCase cashEnsureWalletUseCase;
	private final CashCompleteOrderPaymentUseCase cashCompleteOrderPaymentUseCase;
	private final CashCancelOrderPaymentUseCase cashCancelOrderPaymentUseCase;
	private final CashRefundPaymentUseCase cashRefundPaymentUseCase;
	private final CashChargeConfirmUseCase cashChargeConfirmUseCase;
	private final CashWithdrawalUseCase cashWithdrawalUseCase;
	private final CashPayoutUseCase cashPayoutUseCase;
	private final CashSyncSellerUseCase cashSyncSellerUseCase;
	private final CashWalletLedgerQueryUseCase cashWalletLedgerQueryUseCase;
	private final CashRefundableChargeQueryUseCase cashRefundableChargeQueryUseCase;
	private final CashRecoveryVirtualAccountUseCase cashRecoveryVirtualAccountUseCase;
	private final CashRecoveryDepositUseCase cashRecoveryDepositUseCase;

	@Transactional
	public CashMember syncMember(long id, LocalDateTime createDate, LocalDateTime modifyDate,
		String nickname) {
		CashMember cashMember = cashSyncMemberUseCase.syncMember(id, createDate, modifyDate, nickname);
		cashEnsureWalletUseCase.ensureWallet(cashMember.getId());
		return cashMember;
	}

	@Transactional
	public Wallet ensureWallet(long id) {
		return cashEnsureWalletUseCase.ensureWallet(id);
	}

	@Transactional(readOnly = true)
	public WalletBalanceResponseDto getBalance(long holderId) {
		return WalletBalanceResponseDto.from(cashSupport.findWalletByHolderId(holderId));
	}

	@Transactional(readOnly = true)
	public CashBalanceResponseDto getBalanceOrZero(long memberId) {
		return new CashBalanceResponseDto(cashSupport.findBalanceOrZero(memberId));
	}

	@Transactional(readOnly = true)
	public PageDto<WalletLedgerItemDto> findLedgers(
		long memberId, LedgerCategory category, int page, int size, LocalDateTime now
	) {
		return cashWalletLedgerQueryUseCase.findLedgers(memberId, category, page, size, now);
	}

	@Transactional(readOnly = true)
	public RefundableChargesResponseDto findRefundableCharges(long memberId, LocalDateTime now) {
		return cashRefundableChargeQueryUseCase.findRefundableCharges(memberId, now);
	}

	@Transactional
	public CashPaymentResponseDto completeOrderPayment(long orderId, long customerId, long salePrice) {
		return cashCompleteOrderPaymentUseCase.completeOrderPayment(orderId, customerId, salePrice);
	}

	@Transactional
	public void cancelPayment(long orderId, long customerId) {
		cashCancelOrderPaymentUseCase.cancelPayment(orderId, customerId);
	}

	@Transactional
	public CashRefundResponseDto refund(long orderId, long customerId) {
		return cashRefundPaymentUseCase.refund(orderId, customerId);
	}

	// @Transactional을 일부러 안 붙인다 (PRO-215). Toss 호출이 트랜잭션 밖에서 일어나야 하는데,
	// 여기에 걸면 CashChargeConfirmUseCase 안의 짧은 트랜잭션들이 이 트랜잭션에 합쳐져 버린다.
	public CashChargeResponseDto confirmWalletCharge(long customerId, String paymentKey, String orderId, long amount) {
		return cashChargeConfirmUseCase.confirmWalletCharge(customerId, paymentKey, orderId, amount);
	}

	// @Transactional을 일부러 안 붙인다. 충전 확정과 같은 이유로, 토스 결제취소를 트랜잭션 밖에서 부른다
	public CashWithdrawalResponseDto requestWithdrawal(
		long customerId, String bankCode, String accountNumber, String accountHolder, String idempotencyKey,
		LocalDateTime now
	) {
		return cashWithdrawalUseCase.requestWithdrawal(
			customerId, bankCode, accountNumber, accountHolder, idempotencyKey, now);
	}

	// @Transactional을 일부러 안 붙인다. 충전 확정과 같은 이유로, 은행 송금을 트랜잭션 밖에서 부른다
	public CashPayoutResponseDto payout(
		long payoutId, long sellerId, PayoutStage payoutStage, long settlementAmount, long feeAmount
	) {
		return cashPayoutUseCase.payout(payoutId, sellerId, payoutStage, settlementAmount, feeAmount);
	}

	@Transactional
	public CashSeller syncSeller(SellerChangedEvent event) {
		return cashSyncSellerUseCase.syncSeller(event);
	}

	// @Transactional을 일부러 안 붙인다. 충전 확정과 같은 이유로, 토스 가상계좌 발급을 트랜잭션 밖에서 부른다
	public CashClaimVirtualAccountResponseDto issueClaimVirtualAccount(String claimCode, long amount) {
		return cashRecoveryVirtualAccountUseCase.issue(claimCode, amount);
	}

	// @Transactional을 일부러 안 붙인다. 토스 입금 조회를 트랜잭션 밖에서 부르고, 기록만 짧은 트랜잭션으로 한다
	public void receiveRecoveryDeposit(String claimCode, String secret, String pgStatus) {
		cashRecoveryDepositUseCase.receive(claimCode, secret, pgStatus);
	}
}
