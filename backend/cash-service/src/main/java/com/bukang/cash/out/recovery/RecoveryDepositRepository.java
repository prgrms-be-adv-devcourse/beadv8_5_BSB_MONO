package com.bukang.cash.out.recovery;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.cash.domain.recovery.RecoveryDeposit;
import com.bukang.cash.domain.recovery.RecoveryDepositStatus;

import jakarta.persistence.LockModeType;

public interface RecoveryDepositRepository extends JpaRepository<RecoveryDeposit, Long> {
	Optional<RecoveryDeposit> findByClaimCode(String claimCode);

	List<RecoveryDeposit> findByStatusOrderByIdAsc(RecoveryDepositStatus status);

	List<RecoveryDeposit> findByStatusAndSentAtIsNullOrderByIdAsc(RecoveryDepositStatus status);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select d from RecoveryDeposit d where d.id = :id")
	Optional<RecoveryDeposit> findByIdForUpdate(@Param("id") long id);
}
