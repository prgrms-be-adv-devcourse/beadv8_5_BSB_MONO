package com.bukang.cash.app.payout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.cash.app.CashSupport;
import com.bukang.cash.config.CashWalletProperties;
import com.bukang.cash.domain.exception.payout.PayoutTransferMismatchException;
import com.bukang.cash.domain.payout.PayoutStage;
import com.bukang.cash.domain.payout.PayoutTransfer;
import com.bukang.cash.domain.payout.PayoutTransferType;
import com.bukang.cash.domain.wallet.LedgerRefType;
import com.bukang.cash.domain.wallet.LedgerType;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.dto.payout.CashPayoutResponseDto;
import com.bukang.cash.out.bank.BankTransferClient;
import com.bukang.cash.out.bank.BankTransferResult;
import com.bukang.cash.out.payout.PayoutTransferRepository;
import com.bukang.cash.out.wallet.WalletLedgerRepository;

import lombok.RequiredArgsConstructor;

/**
 * 정산 지급: 대금은 홀딩 지갑에서 빼고 은행으로 판매자 계좌에 송금하고, 최종 정산이면 수수료를 시스템 지갑으로 옮긴다
 * 요청의 sellerId는 판매자 신청서 ID라 회원 ID와 다르다. 은행에는 이 ID를 그대로 보내고, 판매자가 있는지만 CashSupport로 확인한다.
 * 같은 payoutId로 다시 요청했는데 판매자·금액이 이전과 다르면 지갑을 건드리지 않고 저장된 값으로 409 거절한다.
 * 최종 정산은 회수 상계로 대금이 0원이 될 수 있다. 이때도 수수료는 그대로 옮기므로 대금·수수료는 각각 있을 때만 처리한다
 * (둘 다 0원인 호출은 없다고 전제). 대금 이동·수수료 이동은 각각 PayoutTransfer 행을 근거로 삼아 서로 다른 ref_id로 장부에 남긴다.
 */
@Service
@RequiredArgsConstructor
public class CashPayoutUseCase {
	private final CashSupport cashSupport;
	private final CashWalletProperties cashWalletProperties;
	private final PayoutTransferRepository payoutTransferRepository;
	private final WalletLedgerRepository walletLedgerRepository;
	private final BankTransferClient bankTransferClient;
	private final TransactionTemplate transactionTemplate;

	// 은행을 기다리는 동안 홀딩 지갑 잠금을 잡지 않도록 세 단계로 나눈다 (환급과 같은 구조).
	// 1) 잠금 트랜잭션: 수수료 이동, 대금 차감과 송금 기록  2) 트랜잭션 밖: 은행 송금  3) 잠금 트랜잭션: 결과 반영
	public CashPayoutResponseDto payout(
		long payoutId, long sellerId, PayoutStage payoutStage, long settlementAmount, long feeAmount
	) {
		Requested requested = transactionTemplate.execute(status ->
			recordRequestLocked(payoutId, sellerId, payoutStage, settlementAmount, feeAmount));
		if (requested.transferId() == null) {
			return requested.response();
		}
		BankTransferResult result = bankTransferClient.transfer(requested.bankTransferId(), sellerId, settlementAmount);
		return transactionTemplate.execute(status -> applyResultLocked(requested, result));
	}

	private Requested recordRequestLocked(
		long payoutId, long sellerId, PayoutStage payoutStage, long settlementAmount, long feeAmount
	) {
		long holdingMemberId = cashWalletProperties.holdingMemberId();
		long systemMemberId = cashWalletProperties.systemMemberId();
		boolean withSettlement = settlementAmount > 0;
		// 1차 정산은 수수료가 없다. 최종 정산도 상계로 대금만 0원이 되고 수수료는 남을 수 있다
		boolean withFee = payoutStage == PayoutStage.FINAL && feeAmount > 0;

		// 1.없는 판매자에게 송금하지 않도록 먼저 확인한다 (대금이 없으면 확인할 필요도 없다)
		if (withSettlement) {
			cashSupport.resolveSellerMemberId(sellerId);
		}

		// 2.지갑 잠금 (홀딩은 항상, 시스템은 수수료가 있을 때만, ID 작은 쪽부터)
		List<Long> holderIds = new ArrayList<>();
		holderIds.add(holdingMemberId);
		if (withFee) {
			holderIds.add(systemMemberId);
		}
		Map<Long, Wallet> wallets = cashSupport.lockWallets(holderIds.stream().mapToLong(Long::longValue).toArray());
		Wallet holdingWallet = wallets.get(holdingMemberId);

		// 3.같은 payoutId로 이미 처리된 이동이 있으면, 지갑을 잠근 뒤(동시 요청 직렬화) 이번 요청과 값이 같은지 먼저 확인한다
		Optional<PayoutTransfer> existingSettlement =
			payoutTransferRepository.findByPayoutIdAndTransferType(payoutId, PayoutTransferType.SETTLEMENT);
		Optional<PayoutTransfer> existingFee =
			payoutTransferRepository.findByPayoutIdAndTransferType(payoutId, PayoutTransferType.FEE);
		validateConsistentRetry(payoutId, sellerId, settlementAmount, feeAmount, existingSettlement, existingFee);

		// 4.수수료 이동 (홀딩 → 시스템, 최종이고 수수료가 있고 아직 처리 전일 때만)
		boolean feeTransferred = withFee && existingFee.isEmpty();
		if (feeTransferred) {
			PayoutTransfer fee = payoutTransferRepository.save(
				new PayoutTransfer(payoutId, sellerId, PayoutTransferType.FEE, feeAmount));
			walletLedgerRepository.save(holdingWallet.debit(
				feeAmount, LedgerType.정산지급__상품판매_수수료, LedgerRefType.Payout, fee.getId()));
			walletLedgerRepository.save(wallets.get(systemMemberId).credit(
				feeAmount, LedgerType.정산수령__상품판매_수수료, LedgerRefType.Payout, fee.getId()));
		}

		long paidFeeAmount = withFee ? feeAmount : 0;
		// 5.대금 차감과 송금 기록 (대금이 있고 아직 처리 전일 때만). 은행에 보내기 전에 기록해, 서버가 멈춰도 재확인이 이어받는다
		if (!withSettlement || existingSettlement.isPresent()) {
			return Requested.done(feeTransferred
				? CashPayoutResponseDto.paid(settlementAmount, paidFeeAmount)
				: CashPayoutResponseDto.alreadyPaid(settlementAmount, paidFeeAmount));
		}
		PayoutTransfer settlement = payoutTransferRepository.save(
			new PayoutTransfer(payoutId, sellerId, PayoutTransferType.SETTLEMENT, settlementAmount));
		walletLedgerRepository.save(holdingWallet.debit(
			settlementAmount, LedgerType.정산지급__상품판매_대금, LedgerRefType.Payout, settlement.getId()));
		return new Requested(settlement.getId(), settlement.bankTransferId(), settlementAmount, paidFeeAmount, null);
	}

	private CashPayoutResponseDto applyResultLocked(Requested requested, BankTransferResult result) {
		cashSupport.lockWallet(cashWalletProperties.holdingMemberId());
		PayoutTransfer settlement = payoutTransferRepository.findById(requested.transferId()).orElseThrow();
		if (result != BankTransferResult.COMPLETED) {
			throw new IllegalStateException("아직 처리하지 않는 송금 결과입니다. result=" + result);
		}
		settlement.markCompleted();
		return CashPayoutResponseDto.paid(requested.settlementAmount(), requested.paidFeeAmount());
	}

	// 저장된 이동 기록의 판매자·금액이 이번 요청과 다르면, 지갑을 건드리지 않고 저장된 값을 담아 409를 던진다
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

	// transferId가 null이면 은행에 보낼 대금이 없어 response로 바로 답한다
	private record Requested(
		Long transferId, String bankTransferId, long settlementAmount, long paidFeeAmount,
		CashPayoutResponseDto response
	) {
		static Requested done(CashPayoutResponseDto response) {
			return new Requested(null, null, 0, 0, response);
		}
	}
}
