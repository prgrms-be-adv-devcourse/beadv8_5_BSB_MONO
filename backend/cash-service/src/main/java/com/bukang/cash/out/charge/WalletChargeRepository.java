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

	// 처리 중·결과 확인 못 한 충전 금액 합. 지금 확정하려는 주문번호는 빼서 자기 금액을 두 번 세지 않는다
	@Query("select coalesce(sum(c.amount), 0) from WalletCharge c "
		+ "where c.wallet = :wallet and c.idempotencyKey <> :excludedOrderId "
		+ "and c.status in (com.bukang.cash.domain.charge.ChargeStatus.IN_PROGRESS, "
		+ "com.bukang.cash.domain.charge.ChargeStatus.UNCERTAIN)")
	long sumPendingAmountExcluding(@Param("wallet") Wallet wallet, @Param("excludedOrderId") String excludedOrderId);

	@Query("select coalesce(sum(c.amount), 0) from WalletCharge c "
		+ "where c.wallet = :wallet and c.status = com.bukang.cash.domain.charge.ChargeStatus.COMPLETED "
		+ "and c.createDate between :from and :to")
	long sumCompletedAmountBetween(@Param("wallet") Wallet wallet, @Param("from") LocalDateTime from,
		@Param("to") LocalDateTime to);

	// 승인 시각(없으면 충전 시각)이 [from, to)인 완료 충전의 지갑 ID(= 회원 ID)
	@Query("select distinct c.wallet.id from WalletCharge c "
		+ "where c.status = com.bukang.cash.domain.charge.ChargeStatus.COMPLETED "
		+ "and coalesce(c.approvedAt, c.chargeDate) >= :from and coalesce(c.approvedAt, c.chargeDate) < :to")
	List<Long> findWalletIdsWithCompletedChargeApprovedBetween(@Param("from") LocalDateTime from,
		@Param("to") LocalDateTime to);
}
