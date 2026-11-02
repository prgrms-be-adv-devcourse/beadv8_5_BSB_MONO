package com.bukang.cash.dto;

import com.bukang.cash.domain.wallet.Wallet;

/**
 * 지갑 잔액 조회 응답
 */
public record WalletBalanceResponseDto(
	long holderId,
	long balance
) {
	public static WalletBalanceResponseDto from(Wallet wallet) {
		return new WalletBalanceResponseDto(wallet.getId(), wallet.getBalance());
	}
}
