package com.bukang.cash.app;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.cash.domain.member.CashMember;
import com.bukang.cash.domain.payout.PayoutStage;
import com.bukang.cash.domain.seller.CashSeller;
import com.bukang.cash.domain.wallet.LedgerCategory;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.dto.CashBalanceResponseDto;
import com.bukang.cash.dto.CashChargeResponseDto;
import com.bukang.cash.dto.CashPaymentResponseDto;
import com.bukang.cash.dto.CashPayoutResponseDto;
import com.bukang.cash.dto.CashRefundResponseDto;
import com.bukang.cash.dto.CashWithdrawalResponseDto;
import com.bukang.cash.dto.PageDto;
import com.bukang.cash.dto.WalletBalanceResponseDto;
import com.bukang.common.shared.seller.event.SellerChangedEvent;
import com.bukang.cash.dto.WalletLedgerItemDto;


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
	public PageDto<WalletLedgerItemDto> findLedgers(long memberId, LedgerCategory category, int page, int size) {
		return cashWalletLedgerQueryUseCase.findLedgers(memberId, category, page, size);
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

	@Transactional
	public CashWithdrawalResponseDto requestWithdrawal(
		long customerId, String bankCode, String accountNumber, String accountHolder, String idempotencyKey
	) {
		return cashWithdrawalUseCase.requestWithdrawal(
			customerId, bankCode, accountNumber, accountHolder, idempotencyKey);
	}

	@Transactional
	public CashPayoutResponseDto payout(
		long payoutId, long sellerId, PayoutStage payoutStage, long settlementAmount, long feeAmount
	) {
		return cashPayoutUseCase.payout(payoutId, sellerId, payoutStage, settlementAmount, feeAmount);
	}

	@Transactional
	public Optional<CashSeller> syncSeller(SellerChangedEvent event) {
		return cashSyncSellerUseCase.syncSeller(event);
	}
}
