package com.bukang.boundedcontext.payout.out;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bukang.boundedcontext.payout.domain.Payout;

public interface PayoutRepository extends JpaRepository<Payout, Integer> {
}
