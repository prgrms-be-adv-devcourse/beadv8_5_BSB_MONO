package com.bukang.common.shared.kafka.outbox;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Limit;

/**
 * 보낸 지 7일 지난 "보냄" 아웃박스 행을 지운다 (BUK-200, 회원 BUK-41 정리 작업을 옮김). "포기" 행은 사람이 되돌려 다시 보낼 수 있게 남긴다
 * BATCH_SIZE건씩 나눠 지운다. 한 번의 삭제가 각자 트랜잭션이라 큰 락이 오래 걸리지 않는다
 * 서비스는 이 클래스를 상속한 @Service를 만들고, 스케줄러가 하루에 한 번 cleanup(now)를 부른다
 */
public abstract class OutboxCleanup<E extends BaseOutboxKafkaEvent> {
	public static final int BATCH_SIZE = 1_000;
	private static final Duration RETENTION = Duration.ofDays(7);

	private final OutboxKafkaEventRepository<E> repository;

	protected OutboxCleanup(OutboxKafkaEventRepository<E> repository) {
		this.repository = repository;
	}

	// 지운 건수를 돌려준다
	public int cleanup(LocalDateTime now) {
		LocalDateTime before = now.minus(RETENTION);
		int deleted = 0;
		List<Long> ids;
		do {
			ids = repository.findIdsByStatusAndSentDateBefore(OutboxStatus.SENT, before, Limit.of(BATCH_SIZE));
			repository.deleteAllByIdInBatch(ids);
			deleted += ids.size();
		} while (ids.size() == BATCH_SIZE);
		return deleted;
	}
}
