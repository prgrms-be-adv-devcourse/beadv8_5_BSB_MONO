package com.bukang.order.out;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.order.domain.OrderInboxKafkaEvent;

public interface OrderInboxKafkaEventRepository extends JpaRepository<OrderInboxKafkaEvent, Long> {
	boolean existsByConsumerGroupAndMessageId(String consumerGroup, String messageId);

	// 처리한 메시지로 남긴다. 처음이면 1, 이미 있으면 0 (중복 키 예외를 내지 않는다). 네이티브 쿼리라 시각도 직접 넣는다
	@Modifying
	@Query(value = "insert ignore into order_inbox_kafka_event "
		+ "(message_id, consumer_group, topic, processed_date, create_date, modify_date) "
		+ "values (:messageId, :consumerGroup, :topic, :now, :now, :now)", nativeQuery = true)
	int insertIgnore(
		@Param("messageId") String messageId,
		@Param("consumerGroup") String consumerGroup,
		@Param("topic") String topic,
		@Param("now") LocalDateTime now
	);
}
