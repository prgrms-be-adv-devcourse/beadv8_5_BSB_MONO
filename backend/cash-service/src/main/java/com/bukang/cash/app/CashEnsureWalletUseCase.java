package com.bukang.cash.app;

import org.springframework.stereotype.Service;

import com.bukang.cash.domain.member.CashMember;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.out.CashMemberRepository;
import com.bukang.cash.out.WalletRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CashEnsureWalletUseCase {
	private final WalletRepository walletRepository;
	private final CashMemberRepository cashMemberRepository;

	public Wallet ensureWallet(int cashMemberId) {
		return walletRepository.findByHolderId(cashMemberId)
			.orElseGet(() -> {
				CashMember member = cashMemberRepository.getReferenceById(cashMemberId);
				return walletRepository.save(new Wallet(member));
			});
	}
}
