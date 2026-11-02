package com.bukang.cash.out;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bukang.cash.domain.withdrawal.WalletWithdrawal;

public interface WalletWithdrawalRepository extends JpaRepository<WalletWithdrawal, Long> {
	Optional<WalletWithdrawal> findByIdempotencyKey(String idempotencyKey);
}
