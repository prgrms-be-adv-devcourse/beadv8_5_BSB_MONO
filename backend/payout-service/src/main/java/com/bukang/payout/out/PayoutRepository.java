package com.bukang.payout.out;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutStatus;

public interface PayoutRepository extends JpaRepository<Payout, Integer> {
	boolean existsBySellerIdAndRaceIdAndPayoutMonth(int sellerId, int raceId, String payoutMonth);

	// 지급 배치: 지급 예정일이 오늘이거나 지난 정산 내역 (지난 날을 놓쳐도 다음 실행에서 따라잡는다)
	List<Payout> findByStatusAndScheduledPayDateLessThanEqual(PayoutStatus status, LocalDate date);
}
