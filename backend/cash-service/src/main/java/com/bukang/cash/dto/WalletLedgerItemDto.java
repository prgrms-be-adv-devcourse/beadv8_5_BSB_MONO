package com.bukang.cash.dto;

import java.time.LocalDateTime;

import com.bukang.cash.domain.wallet.LedgerCategory;
import com.bukang.cash.domain.wallet.LedgerType;
import com.bukang.cash.domain.wallet.WalletLedger;

/**
 * 예치금 내역 한 줄 (PRO-258). 대회 이름 등은 프론트가 refId로 주문 API와 맞춰서 붙인다
 */
public record WalletLedgerItemDto(
	LedgerCategory category,
	LedgerType ledgerType,
	long amount,
	long balanceAfter,
	LocalDateTime createdAt,
	long refId
) {
	public static WalletLedgerItemDto from(WalletLedger walletLedger) {
		return new WalletLedgerItemDto(
			LedgerCategory.of(walletLedger.getLedgerType()),
			walletLedger.getLedgerType(),
			walletLedger.getAmount(),
			walletLedger.getBalanceAfter(),
			walletLedger.getCreateDate(),
			walletLedger.getRefId()
		);
	}
}
