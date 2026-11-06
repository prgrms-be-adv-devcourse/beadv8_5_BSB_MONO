package com.bukang.cash.out;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.domain.withdrawal.WalletWithdrawal;
import com.bukang.cash.domain.withdrawal.WithdrawalStatus;

public interface WalletWithdrawalRepository extends JpaRepository<WalletWithdrawal, Long> {
	Optional<WalletWithdrawal> findByIdempotencyKey(String idempotencyKey);

	boolean existsByWalletAndStatus(Wallet wallet, WithdrawalStatus status);
}
