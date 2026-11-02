package com.bukang.cash.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.bukang.cash.config.CashWalletProperties;
import com.bukang.cash.domain.exception.PayoutTransferMismatchException;
import com.bukang.cash.domain.payout.PayoutStage;
import com.bukang.cash.domain.payout.PayoutTransfer;
import com.bukang.cash.domain.payout.PayoutTransferType;
import com.bukang.cash.domain.wallet.LedgerRefType;
import com.bukang.cash.domain.wallet.LedgerType;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.dto.CashPayoutResponseDto;
import com.bukang.cash.out.PayoutTransferRepository;
import com.bukang.cash.out.WalletLedgerRepository;

import lombok.RequiredArgsConstructor;

/**
 * 정산 지급: 홀딩 지갑에서 판매자 지갑으로 대금을, 최종 정산이면 시스템 지갑으로 수수료도 함께 옮긴다
 * 요청의 sellerId는 판매자 신청서 ID라 회원 ID(= 지갑 ID)와 다르다. CashSupport로 실제 회원 ID를 찾아 지갑을 잠근다.
 * 같은 payoutId로 다시 요청했는데 판매자·금액이 이전과 다르면 지갑을 건드리지 않고 저장된 값으로 409 거절한다 (리뷰 반영).
 * 최종 정산은 회수 상계로 대금이 0원이 될 수 있다. 이때도 수수료는 상계와 무관하게 그대로 옮기므로,
 * 대금·수수료 이동은 각각 있을 때만 처리하고(둘 다 0원인 호출은 없다고 전제) 판매자 지갑도 대금이 있을 때만 잠근다.
 * 대금 이동·수수료 이동은 각각 독립된 PayoutTransfer 행을 근거로 삼아 서로 다른 ref_id로 장부에 남긴다.
 */
@Service
@RequiredArgsConstructor
public class CashPayoutUseCase {
	private final CashSupport cashSupport;
	private final CashWalletProperties cashWalletProperties;
	private final PayoutTransferRepository payoutTransferRepository;
	private final WalletLedgerRepository walletLedgerRepository;

	public CashPayoutResponseDto payout(
		long payoutId, long sellerId, PayoutStage payoutStage, long settlementAmount, long feeAmount
	) {
		long holdingMemberId = cashWalletProperties.holdingMemberId();
		long systemMemberId = cashWalletProperties.systemMemberId();
		boolean withSettlement = settlementAmount > 0;
		// 1차 정산은 수수료가 없다. 최종 정산도 상계로 대금만 0원이 되고 수수료는 남을 수 있다 (완료 기준)
		boolean withFee = payoutStage == PayoutStage.FINAL && feeAmount > 0;

		// 1.지갑 잠금 (홀딩은 항상, 판매자·시스템은 실제로 돈이 오갈 때만, ID 작은 쪽부터)
		// 대금이 없으면(수수료만 있는 요청) 판매자 ID를 회원 ID로 바꿀 필요도 없다
		List<Long> holderIds = new ArrayList<>();
		holderIds.add(holdingMemberId);
		Long sellerMemberId = null;
		if (withSettlement) {
			sellerMemberId = cashSupport.resolveSellerMemberId(sellerId);
			holderIds.add(sellerMemberId);
		}
		if (withFee) {
			holderIds.add(systemMemberId);
		}
		long[] holderIdArray = holderIds.stream().mapToLong(Long::longValue).toArray();
		Map<Long, Wallet> wallets = cashSupport.lockWallets(holderIdArray);
		Wallet holdingWallet = wallets.get(holdingMemberId);

		// 2.같은 payoutId로 이미 처리된 이동이 있으면, 지갑을 잠근 뒤(동시 요청 직렬화) 이번 요청과 값이 같은지 먼저 확인한다
		Optional<PayoutTransfer> existingSettlement =
			payoutTransferRepository.findByPayoutIdAndTransferType(payoutId, PayoutTransferType.SETTLEMENT);
		Optional<PayoutTransfer> existingFee =
			payoutTransferRepository.findByPayoutIdAndTransferType(payoutId, PayoutTransferType.FEE);
		validateConsistentRetry(payoutId, sellerId, settlementAmount, feeAmount, existingSettlement, existingFee);

		// 3.대금 이동 (홀딩 → 판매자, 대금이 있고 아직 처리 전일 때만)
		boolean settlementTransferred = withSettlement && existingSettlement.isEmpty();
		if (settlementTransferred) {
			transfer(payoutId, sellerId, PayoutTransferType.SETTLEMENT, settlementAmount, holdingWallet,
				wallets.get(sellerMemberId), LedgerType.정산지급__상품판매_대금, LedgerType.정산수령__상품판매_대금);
		}
		// 4.수수료 이동 (홀딩 → 시스템, 최종이고 수수료가 있고 아직 처리 전일 때만)
		boolean feeTransferred = withFee && existingFee.isEmpty();
		if (feeTransferred) {
			transfer(payoutId, sellerId, PayoutTransferType.FEE, feeAmount, holdingWallet,
				wallets.get(systemMemberId), LedgerType.정산지급__상품판매_수수료, LedgerType.정산수령__상품판매_수수료);
		}

		long paidFeeAmount = withFee ? feeAmount : 0;
		return settlementTransferred || feeTransferred
			? CashPayoutResponseDto.paid(settlementAmount, paidFeeAmount)
			: CashPayoutResponseDto.alreadyPaid(settlementAmount, paidFeeAmount);
	}

	// 저장된 이동 기록의 판매자·금액이 이번 요청과 다르면, 지갑을 건드리지 않고 저장된 값을 담아 409를 던진다 (리뷰 반영)
	private void validateConsistentRetry(
		long payoutId, long sellerId, long settlementAmount, long feeAmount,
		Optional<PayoutTransfer> existingSettlement, Optional<PayoutTransfer> existingFee
	) {
		boolean settlementMismatch = existingSettlement
			.map(stored -> stored.getSellerId() != sellerId || stored.getAmount() != settlementAmount)
			.orElse(false);
		boolean feeMismatch = existingFee
			.map(stored -> stored.getSellerId() != sellerId || stored.getAmount() != feeAmount)
			.orElse(false);
		if (settlementMismatch || feeMismatch) {
			long storedSettlementAmount = existingSettlement.map(PayoutTransfer::getAmount).orElse(0L);
			long storedFeeAmount = existingFee.map(PayoutTransfer::getAmount).orElse(0L);
			throw new PayoutTransferMismatchException(
				"같은 정산 ID로 이전과 다른 값이 요청됐습니다. payoutId=%d".formatted(payoutId),
				storedSettlementAmount, storedFeeAmount);
		}
	}

	private void transfer(
		long payoutId, long sellerId, PayoutTransferType transferType, long amount,
		Wallet fromWallet, Wallet toWallet, LedgerType debitType, LedgerType creditType
	) {
		PayoutTransfer transferRecord = payoutTransferRepository.save(
			new PayoutTransfer(payoutId, sellerId, transferType, amount));
		walletLedgerRepository.save(
			fromWallet.debit(amount, debitType, LedgerRefType.Payout, transferRecord.getId()));
		walletLedgerRepository.save(
			toWallet.credit(amount, creditType, LedgerRefType.Payout, transferRecord.getId()));
	}
}
