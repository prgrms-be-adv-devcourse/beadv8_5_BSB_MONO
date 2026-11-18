package com.bukang.payout.out;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bukang.payout.domain.PayoutSellerExportLog;

// 판매자 정산 자료 내려받기 기록
public interface PayoutSellerExportLogRepository extends JpaRepository<PayoutSellerExportLog, Long> {
	List<PayoutSellerExportLog> findBySellerIdOrderByIdDesc(long sellerId);
}
