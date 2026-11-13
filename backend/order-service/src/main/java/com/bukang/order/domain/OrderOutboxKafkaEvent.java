package com.bukang.order.domain;

import static lombok.AccessLevel.PROTECTED;

import com.bukang.common.shared.kafka.outbox.BaseOutboxKafkaEvent;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.NoArgsConstructor;

/**
 * 보낼 Kafka 메시지 1건 (트랜잭셔널 아웃박스). 공통 칸과 상태 변경은 common BaseOutboxKafkaEvent에 있다 (BUK-207)
 * 주문 취소·만료 트랜잭션 안에서 함께 저장하므로, 커밋되면 메시지도 반드시 남고 롤백되면 메시지도 없다.
 * 보내는 일은 OrderOutboxKafkaRelayUseCase가 따로 한다 (1초마다 보낼 차례 행을 읽어 보낸다).
 * 대상은 refType(엔티티 클래스 이름, 예: Participation)·refId로 적는다.
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Table(
	name = "ORDER_OUTBOX_KAFKA_EVENT",
	uniqueConstraints = @UniqueConstraint(name = "uk_order_outbox_kafka_message_id", columnNames = "message_id"),
	indexes = {
		// 보낼 차례 행을 저장 순서(id)대로 찾는다 (발행 작업)
		@Index(name = "idx_order_outbox_kafka_status_id", columnList = "status, id"),
		// 보낸 지 오래된 행을 찾는다 (정리 작업. 주문은 아직 정리하지 않는다)
		@Index(name = "idx_order_outbox_kafka_status_sent", columnList = "status, sent_date")
	}
)
// 주문 표는 토픽 칸 이름이 event_type이다 (common은 topic)
@AttributeOverride(name = "topic", column = @Column(name = "event_type", nullable = false, length = 100))
public class OrderOutboxKafkaEvent extends BaseOutboxKafkaEvent {
	private OrderOutboxKafkaEvent(String refType, long refId, String topic, String payload) {
		super(topic, refType, refId, payload);
	}

	public static OrderOutboxKafkaEvent pending(String refType, long refId, String topic, String payload) {
		return new OrderOutboxKafkaEvent(refType, refId, topic, payload);
	}

	// 메시지 ID에 붙일 서비스 이름 (order-outbox-{UUID})
	@Override
	protected String serviceName() {
		return "order";
	}
}
