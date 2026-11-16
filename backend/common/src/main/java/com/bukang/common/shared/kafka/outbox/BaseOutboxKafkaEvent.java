package com.bukang.common.shared.kafka.outbox;

import static jakarta.persistence.EnumType.STRING;
import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDateTime;
import java.util.Map;

import com.bukang.common.global.jpa.entity.BaseIdAndTime;
import com.bukang.common.shared.kafka.KafkaMessageId;

import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 보낼 Kafka 메시지 1건 (트랜잭셔널 아웃박스, BUK-200). 서비스마다 이 클래스를 상속한 엔티티와 자기 테이블을 둔다
 * 도메인 데이터와 같은 트랜잭션에서 저장하므로, 커밋되면 메시지도 반드시 남고 롤백되면 메시지도 없다.
 * 보내는 일은 OutboxKafkaRelay, 보낸 행을 지우는 일은 OutboxCleanup이 한다.
 * 상속한 엔티티는 @Table에 message_id 유니크 제약과 (status, id), (status, sent_date) 인덱스를 건다.
 * 토픽 컬럼 이름이 topic이 아니면 @AttributeOverride(name = "topic")로 바꾼다
 */
@MappedSuperclass
@NoArgsConstructor(access = PROTECTED)
@Getter
public abstract class BaseOutboxKafkaEvent extends BaseIdAndTime {
	// message-id 헤더로 보낼 값 ({서비스}-outbox-{UUID}). 행을 만들 때 한 번 정해 다시 보내도 같은 값이 나간다
	// 이 컬럼을 더하기 전에 만든 행은 비어 있어(null) 발행 작업이 보내기 전에 채운다
	@Column(name = "message_id", length = 100)
	private String messageId;

	// 보낼 토픽 (KafkaTopics 상수)
	@Column(name = "topic", nullable = false, length = 100)
	private String topic;

	// 이벤트 대상 종류 (대상 엔티티의 클래스 이름, 예: Member)
	@Column(name = "ref_type", nullable = false, length = 50)
	private String refType;

	// 이벤트 대상 ID. 메시지 key로 보낸다. 같은 대상의 행은 저장 순서대로 나간다
	@Column(name = "ref_id", nullable = false)
	private long refId;

	@Column(name = "payload", nullable = false, columnDefinition = "text")
	private String payload;

	@Enumerated(STRING)
	@Column(name = "status", nullable = false, columnDefinition = "varchar(20)")
	private OutboxStatus status;

	// 보내지 못한 횟수 (다시 보낼 간격을 늘리는 데 쓴다)
	@Column(name = "retry_count", nullable = false)
	private int retryCount;

	// 다시 보내도 되는 가장 이른 시각. 한 번도 실패하지 않았으면 null
	@Column(name = "next_retry_date")
	private LocalDateTime nextRetryDate;

	// 브로커가 받았다고 응답한 시각
	@Column(name = "sent_date")
	private LocalDateTime sentDate;

	protected BaseOutboxKafkaEvent(String topic, String refType, long refId, String payload) {
		this.topic = topic;
		this.refType = refType;
		this.refId = refId;
		this.payload = payload;
		this.status = OutboxStatus.PENDING;
		this.messageId = KafkaMessageId.ofOutbox(serviceName());
	}

	// 메시지 ID에 붙일 서비스 이름 (예: member). 생성자에서 부르므로 상수만 돌려준다
	protected abstract String serviceName();

	// message-id 말고 더 붙일 헤더 (이름 → 값). 없으면 빈 맵
	// 값에 null을 넣지 않는다. 보내기 실패가 연결 문제로 분류되어 포기하지 않고, 그 대상의 뒤 행이 계속 막힌다
	public Map<String, String> extraHeaders() {
		return Map.of();
	}

	// message_id 컬럼을 더하기 전에 만든 행(값 없음)에 메시지 ID를 한 번 채운다. 이미 있으면 그대로 둔다
	// 발행 작업 트랜잭션에서 함께 저장되므로, 보내지 못해 다시 보낼 때도 같은 값이 나간다
	public void assignMessageIdIfAbsent() {
		if (messageId == null) {
			this.messageId = KafkaMessageId.ofOutbox(serviceName());
		}
	}

	public void markSent(LocalDateTime now) {
		this.status = OutboxStatus.SENT;
		this.sentDate = now;
	}

	// 보내지 못한 횟수를 올린다. 포기면 더는 보내지 않고, 아니면 nextRetryDate 전에는 다시 보내지 않는다
	public void markSendFailed(boolean giveUp, LocalDateTime nextRetryDate) {
		this.retryCount++;
		if (giveUp) {
			this.status = OutboxStatus.SEND_FAILED;
			return;
		}
		this.nextRetryDate = nextRetryDate;
	}

	public boolean isSendableAt(LocalDateTime now) {
		return nextRetryDate == null || !nextRetryDate.isAfter(now);
	}
}
