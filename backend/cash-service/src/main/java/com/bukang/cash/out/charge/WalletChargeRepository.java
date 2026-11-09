package com.bukang.cash.out.charge;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bukang.cash.domain.charge.ChargeStatus;
import com.bukang.cash.domain.charge.WalletCharge;
import com.bukang.cash.domain.wallet.Wallet;

public interface WalletChargeRepository extends JpaRepository<WalletCharge, Long> {
	Optional<WalletCharge> findByIdempotencyKey(String idempotencyKey);

	List<WalletCharge> findByStatus(ChargeStatus status);

	List<WalletCharge> findByWalletAndStatus(Wallet wallet, ChargeStatus status);

	List<WalletCharge> findByStatusAndModifyDateBefore(ChargeStatus status, LocalDateTime threshold);
}
