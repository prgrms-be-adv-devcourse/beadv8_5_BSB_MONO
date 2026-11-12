package com.bukang.order.domain;

import static lombok.AccessLevel.PROTECTED;

import com.bukang.common.shared.kafka.dlt.BaseDltKafkaEvent;
import com.bukang.common.shared.kafka.dlt.KafkaDltMessage;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.NoArgsConstructor;

/**
 * order가 끝내 처리하지 못해 DLT(<토픽>.order.dlt)로 간 Kafka 메시지 1건. 칸과 상태는 BaseDltKafkaEvent에 있다.
 * 표는 Flyway(V202611121500)가 만든다.
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Table(
	name = "ORDER_DLT_KAFKA_EVENT",
	// 같은 메시지는 행 하나로 상태만 바꾼다. 같은 DLT 메시지를 두 번 받아 저장이 겹쳐도 두 번째를 막는다
	uniqueConstraints = @UniqueConstraint(name = "uk_order_dlt_message", columnNames = "message_id")
)
public class OrderDltKafkaEvent extends BaseDltKafkaEvent {
	// Slack 알림 첫 줄([order]), 내부 API 주소(/internal/v1/order/kafka-failures), 오류 메시지에 쓰는 서비스 이름
	public static final String SERVICE = "order";

	// DLT로 처음 온 메시지를 처리 실패 행으로 만든다
	public OrderDltKafkaEvent(KafkaDltMessage message) {
		super(message);
	}

	@Override
	public String serviceName() {
		return SERVICE;
	}
}
