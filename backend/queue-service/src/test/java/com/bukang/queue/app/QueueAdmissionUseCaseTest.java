package com.bukang.queue.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.bukang.queue.domain.exception.QueueUnavailableException;
import com.bukang.queue.dto.QueueAdmissionDto;
import com.bukang.queue.out.QueueRedisRepository;
import com.bukang.queue.support.QueueIntegrationTest;

/**
 * 입장 확인(QueueAdmissionUseCase)에 고정 시각(NOW)을 넣어 끝 시각과 남은 시간이 정확한지 확인한다.
 * 끝 시각은 실제 Redis 컨테이너의 active에 직접 넣는다.
 * Redis 장애는 공용 Redis 컨테이너를 멈추면 다른 테스트가 깨지므로, 저장소만 가짜(Mockito)로 바꿔 만든다.
 */
class QueueAdmissionUseCaseTest extends QueueIntegrationTest {
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 16, 14, 1);
	private static final long RACE_ID = 1;
	private static final long RACE_COURSE_ID = 11;
	private static final long MEMBER_ID = 7;
	private static final String ACTIVE_KEY = "race:1:racecourse:11:active";
	// QueueRedisRepository가 score(epoch ms)를 만들 때 쓰는 시간대
	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	@Autowired
	private QueueAdmissionUseCase queueAdmissionUseCase;

	@Test
	@DisplayName("getAdmission: 지금이 14:01이고 active 끝 시각이 14:10이면, 끝 시각 14:10과 남은 시간 9분(540000ms)을 받는다")
	void remainingMillisUntilEnd() {
		LocalDateTime activeUntil = LocalDateTime.of(2026, 10, 16, 14, 10);
		redisTemplate.opsForZSet().add(ACTIVE_KEY, "7", epochMillis(activeUntil));

		QueueAdmissionDto admission = queueAdmissionUseCase.getAdmission(MEMBER_ID, RACE_ID, RACE_COURSE_ID, NOW);

		assertThat(admission.isAdmitted()).isTrue();
		assertThat(admission.getAdmissionEndsAt()).isEqualTo(activeUntil);
		assertThat(admission.getRemainingMillis()).isEqualTo(540_000);
	}

	@Test
	@DisplayName("getAdmission: Redis에 연결하지 못하면(저장소가 QueueUnavailableException) 503이 아니라 입장 중이 아님으로 답한다")
	void notAdmittedWhenRedisUnavailable() {
		QueueRedisRepository brokenRepository = mock(QueueRedisRepository.class);
		when(brokenRepository.findActiveUntil(anyLong(), anyLong(), anyLong(), any()))
			.thenThrow(new QueueUnavailableException());
		QueueAdmissionUseCase useCase = new QueueAdmissionUseCase(brokenRepository);

		QueueAdmissionDto admission = useCase.getAdmission(MEMBER_ID, RACE_ID, RACE_COURSE_ID, NOW);

		assertThat(admission.isAdmitted()).isFalse();
		assertThat(admission.getAdmissionEndsAt()).isNull();
		assertThat(admission.getRemainingMillis()).isZero();
	}

	// QueueRedisRepository와 같은 방식으로 score(epoch ms)를 만든다
	private static double epochMillis(LocalDateTime time) {
		return time.atZone(ZONE).toInstant().toEpochMilli();
	}
}
