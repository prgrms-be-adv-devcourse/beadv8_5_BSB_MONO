package com.bukang.cash.dto.wallet;

import java.time.LocalDateTime;

import com.bukang.cash.domain.wallet.LedgerCategory;
import com.bukang.cash.domain.wallet.LedgerType;
import com.bukang.cash.domain.wallet.WalletLedger;
import com.bukang.cash.dto.payment.RefundableChargeDto;

/**
 * 예치금 내역 한 줄 (PRO-258). 대회 이름 등은 프론트가 refId로 주문 API와 맞춰서 붙인다
 * charge는 충전 줄에만 있고(남은 금액·카드 취소 마지막 날 등), 다른 줄은 null이다
 */
public record WalletLedgerItemDto(
	LedgerCategory category,
	LedgerType ledgerType,
	long amount,
	long balanceAfter,
	LocalDateTime createdAt,
	long refId,
	RefundableChargeDto charge
) {
	public static WalletLedgerItemDto from(WalletLedger walletLedger, RefundableChargeDto charge) {
		return new WalletLedgerItemDto(
			LedgerCategory.of(walletLedger.getLedgerType()),
			walletLedger.getLedgerType(),
			walletLedger.getAmount(),
			walletLedger.getBalanceAfter(),
			walletLedger.getCreateDate(),
			walletLedger.getRefId(),
			charge
		);
	}
}
