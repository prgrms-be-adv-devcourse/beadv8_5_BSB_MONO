package com.bukang.order.domain;

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
 * 보낼 Kafka 메시지 1건 (트랜잭셔널 아웃박스, 상품 서비스 ProductOutboxKafkaEvent와 같은 방식)
 * 주문 취소·만료 트랜잭션 안에서 함께 저장하므로, 커밋되면 메시지도 반드시 남고 롤백되면 메시지도 없다.
 * 보내는 일은 OrderOutboxKafkaRelayUseCase가 따로 한다 (1초마다 미발행 행을 읽어 보낸다).
 * 대상은 refType(엔티티 클래스 이름, 예: Participation)·refId로 적는다.
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(
	name = "ORDER_OUTBOX_KAFKA_EVENT",
	// 미발행(PENDING) 행을 만든 순서대로 빠르게 찾는다 (릴레이가 쓴다)
	indexes = @Index(name = "idx_order_outbox_kafka_status_create", columnList = "status, create_date"),
	uniqueConstraints = @UniqueConstraint(name = "uk_order_outbox_kafka_message_id", columnNames = "message_id")
)
public class OrderOutboxKafkaEvent extends BaseIdAndTime {
	// 메시지 ID에 붙일 서비스 이름 (order-outbox-{UUID})
	private static final String SERVICE = "order";

	// message-id 헤더로 보낼 값 (order-outbox-{UUID}). 행을 만들 때 한 번 정해 다시 보내도 같은 값이 나간다
	// 이 칸을 더하기 전에 만든 행은 Flyway(V202611051500)가 채운다
	@Column(name = "message_id", nullable = false, length = 100)
	private String messageId;

	// 이벤트 대상의 종류 (대상 엔티티의 클래스 이름, 예: Participation)
	@Column(name = "ref_type", nullable = false, length = 50)
	private String refType;

	// 이벤트 대상의 ID. 메시지 key로 보내, 같은 대상의 메시지가 같은 파티션에서 순서대로 처리되게 한다
	@Column(name = "ref_id", nullable = false)
	private long refId;

	// 이벤트 종류 = 보낼 토픽 이름 (KafkaTopics 상수)
	@Column(name = "event_type", nullable = false, length = 100)
	private String eventType;

	// 메시지 본문 (JsonConverter로 만든 JSON)
	@Column(name = "payload", nullable = false, columnDefinition = "text")
	private String payload;

	@Enumerated(STRING)
	@Column(name = "status", nullable = false, columnDefinition = "varchar(20)")
	private OutboxStatus status;

	// 보내지 못한 횟수
	@Column(name = "retry_count", nullable = false)
	private int retryCount;

	// 보내지 못했을 때 다시 보내도 되는 가장 이른 시각 (재시도 간격을 점점 늘린다). 한 번도 실패하지 않았으면 null
	@Column(name = "next_retry_date")
	private LocalDateTime nextRetryDate;

	// 브로커가 받았다고 응답한 시각
	@Column(name = "sent_date")
	private LocalDateTime sentDate;

	private OrderOutboxKafkaEvent(String refType, long refId, String eventType, String payload) {
		this.refType = refType;
		this.refId = refId;
		this.eventType = eventType;
		this.payload = payload;
		this.status = OutboxStatus.PENDING;
		this.messageId = KafkaMessageId.ofOutbox(SERVICE);
	}

	public static OrderOutboxKafkaEvent pending(String refType, long refId, String eventType, String payload) {
		return new OrderOutboxKafkaEvent(refType, refId, eventType, payload);
	}

	// 메시지 key (대상 ID)
	public String getMessageKey() {
		return String.valueOf(refId);
	}

	public void markSent(LocalDateTime now) {
		this.status = OutboxStatus.SENT;
		this.sentDate = now;
	}

	// 보내지 못한 횟수를 올리고, maxRetry번째 실패면 실패로 바꿔 더는 보내지 않는다
	// 아직 재시도할 수 있으면 nextRetryDate 전에는 다시 보내지 않는다
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
