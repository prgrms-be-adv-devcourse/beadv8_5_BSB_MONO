package com.bukang.order.domain;

import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDateTime;

import com.bukang.common.global.jpa.entity.BaseIdAndTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 처리를 끝낸 Kafka 메시지 1건 (인박스, BUK-53). 같은 메시지(같은 컨슈머 그룹 + 메시지 ID)가 다시 오면 처리를 건너뛴다
 * 행은 OrderInboxKafkaEventRepository.insertIgnore로만 넣는다 (상품 인박스와 같은 방식)
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(
	name = "ORDER_INBOX_KAFKA_EVENT",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_order_inbox_group_message", columnNames = {"consumer_group", "message_id"})
)
public class OrderInboxKafkaEvent extends BaseIdAndTime {
	// 보낸 쪽이 붙인 message-id 헤더 값 (예: product-outbox-42)
	@Column(name = "message_id", nullable = false, length = 100)
	private String messageId;

	@Column(name = "consumer_group", nullable = false, length = 100)
	private String consumerGroup;

	@Column(name = "topic", nullable = false, length = 100)
	private String topic;

	@Column(name = "processed_date", nullable = false)
	private LocalDateTime processedDate;
}
