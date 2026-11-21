package com.bukang.member.domain;

import static lombok.AccessLevel.PROTECTED;

import com.bukang.common.shared.kafka.dlt.BaseDltKafkaEvent;
import com.bukang.common.shared.kafka.dlt.KafkaDltMessage;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.NoArgsConstructor;

/**
 * member가 끝내 처리하지 못해 실패 토픽(<토픽>.member.dlt)으로 간 Kafka 메시지 1건 (BUK-275). 칸과 상태는 BaseDltKafkaEvent에 있다
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Table(
	name = "MEMBER_DLT_KAFKA_EVENT",
	uniqueConstraints = @UniqueConstraint(name = "uk_member_dlt_message", columnNames = "message_id")
)
public class MemberDltKafkaEvent extends BaseDltKafkaEvent {
	// Slack 알림 첫 줄([member])에 쓰는 서비스 이름
	public static final String SERVICE = "member";

	public MemberDltKafkaEvent(KafkaDltMessage message) {
		super(message);
	}

	@Override
	public String serviceName() {
		return SERVICE;
	}
}
