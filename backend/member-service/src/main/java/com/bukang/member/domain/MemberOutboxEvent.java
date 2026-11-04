package com.bukang.member.domain;

import static jakarta.persistence.EnumType.STRING;
import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDateTime;

import com.bukang.common.global.jpa.entity.BaseIdAndTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 보낼 Kafka 메시지 1건 (트랜잭셔널 아웃박스, BUK-41). 주문 서비스 OrderOutboxKafkaEvent와 거의 같은 컬럼이다
 * 회원·판매자 데이터와 같은 트랜잭션에서 저장하므로, 커밋되면 메시지도 반드시 남고 롤백되면 메시지도 없다.
 * 보내는 일은 MemberOutboxRelayUseCase가 따로 한다
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(
	name = "MEMBER_OUTBOX_EVENT",
	uniqueConstraints = @UniqueConstraint(name = "uk_member_outbox_event_event_id", columnNames = "event_id"),
	indexes = {
		// 보낼 차례 행을 저장 순서(id)대로 찾는다 (발행 작업)
		@Index(name = "idx_member_outbox_event_status_id", columnList = "status, id"),
		// 보낸 지 오래된 행을 찾는다 (정리 작업)
		@Index(name = "idx_member_outbox_event_status_sent", columnList = "status, sent_date")
	}
)
public class MemberOutboxEvent extends BaseIdAndTime {
	// 이벤트 ID (UUID). Kafka 헤더 eventId와 본문 eventId에 같은 값이 들어간다
	@Column(name = "event_id", nullable = false, columnDefinition = "char(36)")
	private String eventId;

	// 보낼 토픽 (KafkaTopics 상수). 헤더 eventType 값으로도 쓴다
	@Column(name = "topic", nullable = false, length = 100)
	private String topic;

	// 이벤트 대상 종류 (Member, Seller)
	@Column(name = "ref_type", nullable = false, length = 50)
	private String refType;

	// 이벤트 대상 ID. 메시지 key로 보낸다. 같은 대상의 행은 저장 순서대로 나간다
	@Column(name = "ref_id", nullable = false)
	private long refId;

	@Column(name = "payload", nullable = false, columnDefinition = "text")
	private String payload;

	@Column(name = "occurred_at", nullable = false)
	private LocalDateTime occurredAt;

	@Enumerated(STRING)
	@Column(name = "status", nullable = false, columnDefinition = "varchar(20)")
	private MemberOutboxStatus status;

	// 보내지 못한 횟수 (다시 보낼 간격을 늘리는 데 쓴다)
	@Column(name = "retry_count", nullable = false)
	private int retryCount;

	// 다시 보내도 되는 가장 이른 시각. 한 번도 실패하지 않았으면 null
	@Column(name = "next_retry_date")
	private LocalDateTime nextRetryDate;

	// 브로커가 받았다고 응답한 시각
	@Column(name = "sent_date")
	private LocalDateTime sentDate;

	private MemberOutboxEvent(String eventId, String topic, String refType, long refId, String payload,
		LocalDateTime occurredAt) {
		this.eventId = eventId;
		this.topic = topic;
		this.refType = refType;
		this.refId = refId;
		this.payload = payload;
		this.occurredAt = occurredAt;
		this.status = MemberOutboxStatus.PENDING;
	}

	public static MemberOutboxEvent pending(String eventId, String topic, String refType, long refId, String payload,
		LocalDateTime occurredAt) {
		return new MemberOutboxEvent(eventId, topic, refType, refId, payload, occurredAt);
	}

	public void markSent(LocalDateTime now) {
		this.status = MemberOutboxStatus.SENT;
		this.sentDate = now;
	}

	// 보내지 못한 횟수를 올린다. 포기면 더는 보내지 않고, 아니면 nextRetryDate 전에는 다시 보내지 않는다
	public void markSendFailed(boolean giveUp, LocalDateTime nextRetryDate) {
		this.retryCount++;
		if (giveUp) {
			this.status = MemberOutboxStatus.SEND_FAILED;
			return;
		}
		this.nextRetryDate = nextRetryDate;
	}

	public boolean isSendableAt(LocalDateTime now) {
		return nextRetryDate == null || !nextRetryDate.isAfter(now);
	}
}
