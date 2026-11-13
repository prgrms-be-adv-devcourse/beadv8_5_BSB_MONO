package com.bukang.member.out;

import com.bukang.common.shared.kafka.outbox.OutboxKafkaEventRepository;
import com.bukang.member.domain.MemberOutboxEvent;

// 조회 메서드는 common OutboxKafkaEventRepository에 있다 (BUK-200)
public interface MemberOutboxEventRepository extends OutboxKafkaEventRepository<MemberOutboxEvent> {
}
