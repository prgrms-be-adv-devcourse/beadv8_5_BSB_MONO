package com.bukang.cash.app.wallet;

import org.springframework.stereotype.Service;

import com.bukang.cash.domain.member.CashMember;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.out.member.CashMemberRepository;
import com.bukang.cash.out.wallet.WalletRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CashEnsureWalletUseCase {
	private final WalletRepository walletRepository;
	private final CashMemberRepository cashMemberRepository;

	public Wallet ensureWallet(long cashMemberId) {
		return walletRepository.findByHolderId(cashMemberId)
			.orElseGet(() -> {
				CashMember member = cashMemberRepository.getReferenceById(cashMemberId);
				return walletRepository.save(new Wallet(member));
			});
	}
}
