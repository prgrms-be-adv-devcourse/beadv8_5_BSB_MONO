package com.bukang.cash.out;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bukang.cash.domain.charge.WalletCharge;

public interface WalletChargeRepository extends JpaRepository<WalletCharge, Long> {
	Optional<WalletCharge> findByIdempotencyKey(String idempotencyKey);
}
