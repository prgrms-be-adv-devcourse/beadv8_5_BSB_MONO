package com.bukang.payout.out;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bukang.payout.domain.PayoutStatusHistory;

// 상태 이력은 Payout을 저장할 때 cascade로 함께 저장된다. 이 리포지토리는 따로 조회 · 정리할 때만 쓴다
public interface PayoutStatusHistoryRepository extends JpaRepository<PayoutStatusHistory, Long> {
}
