package com.bukang.order.out;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import com.bukang.common.shared.kafka.dlt.DltKafkaEventStatus;
import com.bukang.order.domain.OrderDltKafkaEvent;

public interface OrderDltKafkaEventRepository extends JpaRepository<OrderDltKafkaEvent, Long> {
	// 같은 메시지의 행을 찾는다. message_id 유니크 제약이 있어 많아야 1건이다
	Optional<OrderDltKafkaEvent> findByMessageId(String messageId);

	// 상태별 행을 최근 것부터 limit건 읽는다 (내부 API 목록)
	List<OrderDltKafkaEvent> findByStatusOrderByIdDesc(DltKafkaEventStatus status, Limit limit);
}
