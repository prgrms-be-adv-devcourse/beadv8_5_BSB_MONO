package com.bukang.product.out;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import com.bukang.product.domain.OutboxStatus;
import com.bukang.product.domain.ProductOutboxKafkaEvent;

public interface ProductOutboxKafkaEventRepository extends JpaRepository<ProductOutboxKafkaEvent, Long> {
	// 상태별 이벤트를 만든 순서대로 limit건 읽는다 (만든 시각이 같으면 ID 순)
	List<ProductOutboxKafkaEvent> findByStatusOrderByCreateDateAscIdAsc(OutboxStatus status, Limit limit);
}
