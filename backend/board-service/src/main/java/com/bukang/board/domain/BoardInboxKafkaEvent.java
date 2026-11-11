package com.bukang.board.domain;

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
 * 처리를 끝낸 Kafka 메시지 1건 (트랜잭셔널 인박스, 상품 서비스와 같은 모양).
 * 메시지 처리와 같은 트랜잭션에서 남기므로, 처리가 커밋되면 행도 남고 롤백되면 행도 없다.
 * 행은 BoardInboxKafkaEventRepository.insertIgnore로만 넣는다 (중복이면 예외 없이 0행).
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(
	name = "BOARD_INBOX_KAFKA_EVENT",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_board_inbox_group_message", columnNames = {"consumer_group", "message_id"})
)
public class BoardInboxKafkaEvent extends BaseIdAndTime {
	// 보낸 쪽이 붙인 message-id 헤더 값 (예: member-outbox-{UUID})
	@Column(name = "message_id", nullable = false, length = 100)
	private String messageId;

	// 메시지를 받은 컨슈머 그룹
	@Column(name = "consumer_group", nullable = false, length = 100)
	private String consumerGroup;

	// 메시지를 받은 토픽 (확인용)
	@Column(name = "topic", nullable = false, length = 100)
	private String topic;

	// 처리를 끝낸 시각
	@Column(name = "processed_date", nullable = false)
	private LocalDateTime processedDate;
}
