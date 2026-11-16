package com.bukang.member.app;

import org.springframework.stereotype.Service;

import com.bukang.common.shared.kafka.outbox.OutboxCleanup;
import com.bukang.member.domain.MemberOutboxEvent;
import com.bukang.member.out.MemberOutboxEventRepository;

/**
 * 보낸 지 7일 지난 회원 아웃박스 행을 지운다 (BUK-41). 지우는 방식은 common OutboxCleanup에 있다 (BUK-200)
 */
@Service
public class MemberOutboxCleanupUseCase extends OutboxCleanup<MemberOutboxEvent> {
	public MemberOutboxCleanupUseCase(MemberOutboxEventRepository memberOutboxEventRepository) {
		super(memberOutboxEventRepository);
	}
}
