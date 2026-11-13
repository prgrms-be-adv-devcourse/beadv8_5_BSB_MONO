package com.bukang.member.domain;

import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import com.bukang.common.shared.kafka.KafkaEventHeaders;
import com.bukang.common.shared.kafka.outbox.BaseOutboxKafkaEvent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 보낼 Kafka 메시지 1건 (트랜잭셔널 아웃박스, BUK-41). 공통 컬럼과 상태 변경은 common BaseOutboxKafkaEvent에 있다 (BUK-200)
 * 회원·판매자 데이터와 같은 트랜잭션에서 저장하므로, 커밋되면 메시지도 반드시 남고 롤백되면 메시지도 없다.
 * 보내는 일은 MemberOutboxRelayUseCase가 따로 한다. 회원 아웃박스만 이벤트 ID·발생 시각 컬럼과 헤더가 더 있다
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(
	name = "MEMBER_OUTBOX_EVENT",
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_member_outbox_event_event_id", columnNames = "event_id"),
		@UniqueConstraint(name = "uk_member_outbox_event_message_id", columnNames = "message_id")
	},
	indexes = {
		// 보낼 차례 행을 저장 순서(id)대로 찾는다 (발행 작업)
		@Index(name = "idx_member_outbox_event_status_id", columnList = "status, id"),
		// 보낸 지 오래된 행을 찾는다 (정리 작업)
		@Index(name = "idx_member_outbox_event_status_sent", columnList = "status, sent_date")
	}
)
public class MemberOutboxEvent extends BaseOutboxKafkaEvent {
	// 이벤트 ID (UUID). Kafka 헤더 eventId와 본문 eventId에 같은 값이 들어간다
	@Column(name = "event_id", nullable = false, columnDefinition = "char(36)")
	private String eventId;

	@Column(name = "occurred_at", nullable = false)
	private LocalDateTime occurredAt;

	private MemberOutboxEvent(String eventId, String topic, String refType, long refId, String payload,
		LocalDateTime occurredAt) {
		super(topic, refType, refId, payload);
		this.eventId = eventId;
		this.occurredAt = occurredAt;
	}

	public static MemberOutboxEvent pending(String eventId, String topic, String refType, long refId, String payload,
		LocalDateTime occurredAt) {
		return new MemberOutboxEvent(eventId, topic, refType, refId, payload, occurredAt);
	}

	// 메시지 ID에 붙일 서비스 이름 (member-outbox-{UUID})
	@Override
	protected String serviceName() {
		return "member";
	}

	// 받는 쪽이 본문을 열지 않고도 이벤트 ID·종류·대상을 알게 헤더로 보낸다. 대상 ID는 Kafka key와 같다
	@Override
	public Map<String, String> extraHeaders() {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put(KafkaEventHeaders.EVENT_ID, eventId);
		headers.put(KafkaEventHeaders.EVENT_TYPE, getTopic());
		headers.put(KafkaEventHeaders.OCCURRED_AT, occurredAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
		headers.put(KafkaEventHeaders.AGGREGATE_TYPE, getRefType());
		headers.put(KafkaEventHeaders.AGGREGATE_ID, String.valueOf(getRefId()));
		return headers;
	}
}
