package com.bukang.board.domain;

import static jakarta.persistence.EnumType.STRING;
import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDateTime;

import com.bukang.common.global.jpa.entity.BaseIdAndTime;
import com.bukang.common.shared.kafka.KafkaMessageId;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 보낼 Kafka 메시지 1건 (트랜잭셔널 아웃박스, 상품 ProductOutboxKafkaEvent와 같은 모양).
 * 글을 저장하는 트랜잭션 안에서 함께 저장하므로, 저장이 커밋되면 메시지도 반드시 남고 롤백되면 메시지도 없다.
 * 보내는 일은 BoardOutboxKafkaRelayUseCase가 따로 한다.
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(
	name = "BOARD_OUTBOX_KAFKA_EVENT",
	indexes = @Index(name = "idx_board_outbox_kafka_status_create", columnList = "status, create_date"),
	uniqueConstraints = @UniqueConstraint(name = "uk_board_outbox_kafka_message_id", columnNames = "message_id")
)
public class BoardOutboxKafkaEvent extends BaseIdAndTime {
	// 메시지 ID에 붙일 서비스 이름 (board-outbox-{UUID})
	private static final String SERVICE = "board";

	// message-id 헤더로 보낼 값. 행을 만들 때 한 번 정해 다시 보내도 같은 값이 나간다
	@Column(name = "message_id", nullable = false, length = 100)
	private String messageId;

	// 이벤트 대상의 종류 (대상 엔티티의 클래스 이름, 예: BoardPost)
	@Column(name = "ref_type", nullable = false, length = 50)
	private String refType;

	// 이벤트 대상의 ID. 메시지 key로 보내, 같은 대상의 메시지가 같은 파티션에서 순서대로 처리되게 한다
	@Column(name = "ref_id", nullable = false)
	private long refId;

	// 이벤트 종류 = 보낼 토픽 이름 (KafkaTopics 상수)
	@Column(name = "event_type", nullable = false, length = 100)
	private String eventType;

	@Column(name = "payload", nullable = false, columnDefinition = "text")
	private String payload;

	@Enumerated(STRING)
	@Column(name = "status", nullable = false, columnDefinition = "varchar(20)")
	private OutboxStatus status;

	// 보내지 못한 횟수
	@Column(name = "retry_count", nullable = false)
	private int retryCount;

	// 보내지 못했을 때 다시 보내도 되는 가장 이른 시각. 한 번도 실패하지 않았으면 null
	@Column(name = "next_retry_date")
	private LocalDateTime nextRetryDate;

	// 브로커가 받았다고 응답한 시각
	@Column(name = "sent_date")
	private LocalDateTime sentDate;

	private BoardOutboxKafkaEvent(String refType, long refId, String eventType, String payload) {
		this.refType = refType;
		this.refId = refId;
		this.eventType = eventType;
		this.payload = payload;
		this.status = OutboxStatus.PENDING;
		this.messageId = KafkaMessageId.ofOutbox(SERVICE);
	}

	public static BoardOutboxKafkaEvent pending(String refType, long refId, String eventType, String payload) {
		return new BoardOutboxKafkaEvent(refType, refId, eventType, payload);
	}

	// 메시지 key (대상 ID)
	public String getMessageKey() {
		return String.valueOf(refId);
	}

	public void markSent(LocalDateTime now) {
		this.status = OutboxStatus.SENT;
		this.sentDate = now;
	}

	// 보내지 못한 횟수를 올리고, maxRetry번째 실패면 SEND_FAILED로 바꿔 더는 보내지 않는다
	public void markSendFailed(int maxRetry, LocalDateTime nextRetryDate) {
		this.retryCount++;
		if (retryCount >= maxRetry) {
			this.status = OutboxStatus.SEND_FAILED;
			return;
		}
		this.nextRetryDate = nextRetryDate;
	}

	// 지금 보내도 되는지 (실패한 적이 없거나, 재시도 시각이 지났다)
	public boolean isSendableAt(LocalDateTime now) {
		return nextRetryDate == null || !nextRetryDate.isAfter(now);
	}

	public boolean isFailed() {
		return status == OutboxStatus.SEND_FAILED;
	}
}
