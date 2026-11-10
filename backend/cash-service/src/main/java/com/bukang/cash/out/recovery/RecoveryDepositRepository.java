package com.bukang.cash.out.recovery;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bukang.cash.domain.recovery.RecoveryDeposit;

public interface RecoveryDepositRepository extends JpaRepository<RecoveryDeposit, Long> {
	Optional<RecoveryDeposit> findByClaimCode(String claimCode);
}
