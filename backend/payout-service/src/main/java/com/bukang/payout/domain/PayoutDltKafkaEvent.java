package com.bukang.payout.domain;

import static lombok.AccessLevel.PROTECTED;

import com.bukang.common.shared.kafka.dlt.BaseDltKafkaEvent;
import com.bukang.common.shared.kafka.dlt.KafkaDltMessage;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.NoArgsConstructor;

/**
 * 정산(payout)이 끝내 처리하지 못해 DLT(<토픽>.payout.dlt)로 간 Kafka 메시지 1건. 칸과 상태는 BaseDltKafkaEvent에 있다.
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Table(
	name = "PAYOUT_DLT_KAFKA_EVENT",
	// 같은 메시지는 행 하나로 상태만 바꾼다. 같은 DLT 메시지를 두 번 받아 저장이 겹쳐도 두 번째를 막는다
	uniqueConstraints = @UniqueConstraint(name = "uk_payout_dlt_message", columnNames = "message_id")
)
public class PayoutDltKafkaEvent extends BaseDltKafkaEvent {
	// Slack 알림 첫 줄([payout]), 내부 API 주소(/internal/v1/payout/kafka-failures), 오류 메시지에 쓰는 서비스 이름
	// 행이 없을 때(404)는 UseCase가 이 상수를 직접 쓴다
	public static final String SERVICE = "payout";

	// DLT로 처음 온 메시지를 처리 실패 행으로 만든다
	public PayoutDltKafkaEvent(KafkaDltMessage message) {
		super(message);
	}

	@Override
	public String serviceName() {
		return SERVICE;
	}
}
