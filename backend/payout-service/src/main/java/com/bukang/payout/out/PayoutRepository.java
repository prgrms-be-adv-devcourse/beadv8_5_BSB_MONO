package com.bukang.payout.out;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bukang.payout.domain.Payout;

public interface PayoutRepository extends JpaRepository<Payout, Integer> {
	boolean existsBySellerIdAndRaceIdAndPayoutMonth(int sellerId, int raceId, String payoutMonth);
}
