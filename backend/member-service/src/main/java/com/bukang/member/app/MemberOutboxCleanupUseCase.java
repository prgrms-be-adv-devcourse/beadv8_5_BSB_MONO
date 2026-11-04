package com.bukang.member.app;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import com.bukang.member.domain.MemberOutboxStatus;
import com.bukang.member.out.MemberOutboxEventRepository;

import lombok.RequiredArgsConstructor;

/**
 * 보낸 지 7일 지난 "보냄" 아웃박스 행을 지운다 (BUK-41). "포기" 행은 사람이 되돌려 다시 보낼 수 있게 남긴다
 * BATCH_SIZE건씩 나눠 지운다. 한 번의 삭제가 각자 트랜잭션이라 큰 락이 오래 걸리지 않는다
 */
@Service
@RequiredArgsConstructor
public class MemberOutboxCleanupUseCase {
	static final int BATCH_SIZE = 1_000;
	private static final Duration RETENTION = Duration.ofDays(7);

	private final MemberOutboxEventRepository memberOutboxEventRepository;

	// 지운 건수를 돌려준다
	public int cleanup(LocalDateTime now) {
		LocalDateTime before = now.minus(RETENTION);
		int deleted = 0;
		List<Long> ids;
		do {
			ids = memberOutboxEventRepository.findIdsByStatusAndSentDateBefore(
				MemberOutboxStatus.SENT, before, Limit.of(BATCH_SIZE));
			memberOutboxEventRepository.deleteAllByIdInBatch(ids);
			deleted += ids.size();
		} while (ids.size() == BATCH_SIZE);
		return deleted;
	}
}
