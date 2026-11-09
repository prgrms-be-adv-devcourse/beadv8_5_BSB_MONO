package com.bukang.payout.out;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.bukang.payout.domain.PayoutClaim;
import com.bukang.payout.domain.PayoutClaimStatus;

import jakarta.persistence.LockModeType;

public interface PayoutClaimRepository extends JpaRepository<PayoutClaim, Long> {
	// 판매자 × 대회 청구는 하나라 이미 있으면 다시 만들지 않는다
	boolean existsBySellerIdAndRaceId(long sellerId, long raceId);

	// 청구 배치: 아직 입금을 확인하지 않은 청구에 알림을 남긴다
	List<PayoutClaim> findByStatusOrderByIdAsc(PayoutClaimStatus status);

	// 입금 확인: 두 관리자가 동시에 눌러도 늦은 쪽이 '이미 확인'으로 실패하고, 배치의 알림 기록과 같은 행을 동시에 바꾸지 않게 잠근다
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from PayoutClaim c where c.id = :id")
	Optional<PayoutClaim> findByIdForUpdate(long id);

	// 관리자 회수 관리 목록: 최근 청구부터
	Page<PayoutClaim> findAllByOrderByIdDesc(Pageable pageable);

	Page<PayoutClaim> findByStatusOrderByIdDesc(PayoutClaimStatus status, Pageable pageable);

	Optional<PayoutClaim> findByClaimCode(String claimCode);

	// 관리자 회수 관리 카드 숫자
	long countByStatus(PayoutClaimStatus status);

	@Query("select coalesce(sum(c.amount), 0) from PayoutClaim c where c.status = :status")
	long sumAmountByStatus(PayoutClaimStatus status);

	long countByConfirmedAtGreaterThanEqualAndConfirmedAtLessThan(LocalDateTime from, LocalDateTime to);
}
