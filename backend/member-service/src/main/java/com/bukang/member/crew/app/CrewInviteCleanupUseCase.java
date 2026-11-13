package com.bukang.member.crew.app;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import com.bukang.member.crew.out.CrewInviteRepository;

import lombok.RequiredArgsConstructor;

/**
 * 만료되거나 취소된 지 7일 지난 크루 초대를 지운다 (BUK-100)
 * 가입에 쓴 초대(크루원 행이 가리킴, 옛 방식으로 쓰임)는 남긴다
 * BATCH_SIZE건씩 나눠 지운다. 한 번의 삭제가 각자 트랜잭션이라 큰 락이 오래 걸리지 않는다
 */
@Service
@RequiredArgsConstructor
public class CrewInviteCleanupUseCase {
	static final int BATCH_SIZE = 1_000;
	private static final Duration RETENTION = Duration.ofDays(7);

	private final CrewInviteRepository crewInviteRepository;

	// 지운 건수를 돌려준다
	public int cleanup(LocalDateTime now) {
		LocalDateTime before = now.minus(RETENTION);
		int deleted = 0;
		List<Long> ids;
		do {
			ids = crewInviteRepository.findCleanableIds(before, Limit.of(BATCH_SIZE));
			crewInviteRepository.deleteAllByIdInBatch(ids);
			deleted += ids.size();
		} while (ids.size() == BATCH_SIZE);
		return deleted;
	}
}
