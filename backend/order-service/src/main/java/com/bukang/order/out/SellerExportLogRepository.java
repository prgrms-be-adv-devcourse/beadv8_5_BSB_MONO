package com.bukang.order.out;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bukang.order.domain.SellerExportLog;

// 판매자 목록 내려받기 기록 (BUK-228)
public interface SellerExportLogRepository extends JpaRepository<SellerExportLog, Long> {
	List<SellerExportLog> findBySellerIdOrderByIdDesc(long sellerId);
}
