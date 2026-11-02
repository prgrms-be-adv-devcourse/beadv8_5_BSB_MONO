package com.bukang.order.out;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import com.bukang.order.domain.OrderOutboxKafkaEvent;
import com.bukang.order.domain.OutboxStatus;

public interface OrderOutboxKafkaEventRepository extends JpaRepository<OrderOutboxKafkaEvent, Long> {
	// 상태별 이벤트를 만든 순서대로 limit건 읽는다 (만든 시각이 같으면 ID 순)
	List<OrderOutboxKafkaEvent> findByStatusOrderByCreateDateAscIdAsc(OutboxStatus status, Limit limit);
}
