package com.bukang.cash.out.charge;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.cash.domain.charge.ChargeStatus;
import com.bukang.cash.domain.charge.WalletCharge;
import com.bukang.cash.domain.wallet.Wallet;

public interface WalletChargeRepository extends JpaRepository<WalletCharge, Long> {
	Optional<WalletCharge> findByIdempotencyKey(String idempotencyKey);

	List<WalletCharge> findByStatus(ChargeStatus status);

	List<WalletCharge> findByWalletAndStatus(Wallet wallet, ChargeStatus status);

	List<WalletCharge> findByStatusAndModifyDateBefore(ChargeStatus status, LocalDateTime threshold);

	boolean existsByWalletAndReviewFlaggedTrue(Wallet wallet);

	@Query("select coalesce(sum(c.amount), 0) from WalletCharge c "
		+ "where c.wallet = :wallet and c.status = com.bukang.cash.domain.charge.ChargeStatus.COMPLETED "
		+ "and c.createDate between :from and :to")
	long sumCompletedAmountBetween(@Param("wallet") Wallet wallet, @Param("from") LocalDateTime from,
		@Param("to") LocalDateTime to);
}
