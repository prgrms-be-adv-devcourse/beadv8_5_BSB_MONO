package com.bukang.member.app;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.bukang.common.shared.kafka.outbox.OutboxKafkaRelay;
import com.bukang.member.domain.MemberOutboxEvent;
import com.bukang.member.out.MemberOutboxEventRepository;

/**
 * 회원 아웃박스의 보낼 차례 행을 Kafka에 보낸다 (BUK-41). 보내는 방식은 common OutboxKafkaRelay에 있다 (BUK-200)
 */
@Service
public class MemberOutboxRelayUseCase extends OutboxKafkaRelay<MemberOutboxEvent> {
	public MemberOutboxRelayUseCase(MemberOutboxEventRepository memberOutboxEventRepository,
		KafkaTemplate<String, String> kafkaTemplate) {
		super(memberOutboxEventRepository, kafkaTemplate);
	}
}
