package com.bukang.queue.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.bukang.queue.config.QueueProperties;
import com.bukang.queue.out.QueueRedisRepository.Promotion;
import com.bukang.queue.support.QueueIntegrationTest;

/**
 * 대기열 승격(QueuePromoteUseCase)을 실제 Redis 컨테이너로 확인한다.
 * 승격 스케줄러는 test 프로파일에서 등록하지 않으므로, UseCase에 고정 시각(NOW)을 넣어 직접 부른다.
 * 줄(waiting·active)과 스케줄러 목록(queue:waiting)은 Redis에 직접 넣어 만든다.
 */
class QueuePromoteUseCaseTest extends QueueIntegrationTest {
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 16, 14, 0);
	private static final String WAITING_KEY = "race:1:racecourse:11:waiting";
	private static final String ACTIVE_KEY = "race:1:racecourse:11:active";
	private static final String OTHER_WAITING_KEY = "race:2:racecourse:21:waiting";
	private static final String OTHER_ACTIVE_KEY = "race:2:racecourse:21:active";
	private static final String QUEUES_KEY = "queue:waiting";
	private static final String QUEUE_ID = "1:11";
	private static final String OTHER_QUEUE_ID = "2:21";
	// QueueRedisRepository가 score(epoch ms)를 만들 때 쓰는 시간대
	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	@Autowired
	private QueuePromoteUseCase queuePromoteUseCase;
	@Autowired
	private QueueProperties queueProperties;

	@Test
	@DisplayName("promote: 21명이 1초 간격으로 줄을 서 있으면 먼저 선 20명(queue.schedule.size)만 active로 옮기고, "
		+ "가장 늦게 선 1명은 waiting에 남는다")
	void promoteOnlySizeFromFront() {
		int size = queueProperties.schedule().size();
		// 회원 n은 13:59:00에서 n초 뒤에 줄을 섰다 (회원 1이 가장 먼저)
		for (long memberId = 1; memberId <= size + 1; memberId++) {
			addMember(WAITING_KEY, memberId, NOW.minusMinutes(1).plusSeconds(memberId));
		}
		registerQueue(QUEUE_ID);

		Promotion promotion = queuePromoteUseCase.promote(NOW);

		assertThat(promotion.promoted()).isEqualTo(size);
		assertThat(redisTemplate.opsForZSet().size(ACTIVE_KEY)).isEqualTo(size);
		// 대기열에 남은 회원이 21 인지 비교
		assertThat(redisTemplate.opsForZSet().range(WAITING_KEY, 0, -1)).containsExactly(String.valueOf(size + 1));
	}

	@Test
	@DisplayName("promote: 승격한 회원의 active score는 끝 시각인 승격 시각 + 10분(14:00 → 14:10)이다")
	void setActiveUntil() {
		addMember(WAITING_KEY, 7, NOW.minusMinutes(1));
		registerQueue(QUEUE_ID);

		queuePromoteUseCase.promote(NOW);

		assertThat(redisTemplate.opsForZSet().score(ACTIVE_KEY, "7")).isEqualTo(epochMillis(NOW.plusMinutes(10)));
	}

	@Test
	@DisplayName("promote: active 끝 시각이 1분 전에 지난 회원은 지우고, 5분 뒤에 끝나는 회원은 그대로 둔다")
	void removeExpiredActive() {
		addMember(ACTIVE_KEY, 7, NOW.minusMinutes(1));
		addMember(ACTIVE_KEY, 8, NOW.plusMinutes(5));
		registerQueue(QUEUE_ID);

		Promotion promotion = queuePromoteUseCase.promote(NOW);

		assertThat(promotion.expired()).isEqualTo(1);
		assertThat(redisTemplate.opsForZSet().range(ACTIVE_KEY, 0, -1)).containsExactly("8");
	}

	@Test
	@DisplayName("promote: 끝 시각이 지난 active를 지운 뒤 waiting과 active가 모두 비면, 대기열을 스케줄러 목록(queue:waiting)에서 뺀다")
	void removeEmptyQueueFromList() {
		addMember(ACTIVE_KEY, 7, NOW.minusMinutes(1));
		registerQueue(QUEUE_ID);

		queuePromoteUseCase.promote(NOW);

		assertThat(redisTemplate.opsForSet().isMember(QUEUES_KEY, QUEUE_ID)).isFalse();
	}

	@Test
	@DisplayName("promote: 마지막 회원을 승격해 waiting이 비어도 active가 남아 있으면, 나중에 만료 정리를 하도록 목록에 남긴다")
	void keepQueueWhileActiveRemains() {
		addMember(WAITING_KEY, 7, NOW.minusMinutes(1));
		registerQueue(QUEUE_ID);

		queuePromoteUseCase.promote(NOW);

		assertThat(redisTemplate.hasKey(WAITING_KEY)).isFalse();
		assertThat(redisTemplate.opsForSet().isMember(QUEUES_KEY, QUEUE_ID)).isTrue();
	}

	@Test
	@DisplayName("promote: 대기열(대회 1·종목 11, 대회 2·종목 21)마다 자기 줄에서 따로 승격한다")
	void promoteEachQueueSeparately() {
		addMember(WAITING_KEY, 7, NOW.minusMinutes(1));
		addMember(OTHER_WAITING_KEY, 8, NOW.minusMinutes(1));
		registerQueue(QUEUE_ID);
		registerQueue(OTHER_QUEUE_ID);

		Promotion promotion = queuePromoteUseCase.promote(NOW);

		assertThat(promotion.promoted()).isEqualTo(2);
		assertThat(redisTemplate.opsForZSet().range(ACTIVE_KEY, 0, -1)).containsExactly("7");
		assertThat(redisTemplate.opsForZSet().range(OTHER_ACTIVE_KEY, 0, -1)).containsExactly("8");
	}

	@Test
	@DisplayName("promote: 목록에 형식이 틀린 값(bad)이 섞여 있어도, 그 값만 건너뛰고 다른 대기열은 승격한다")
	void skipBrokenQueueId() {
		addMember(WAITING_KEY, 7, NOW.minusMinutes(1));
		registerQueue("bad"); //잘못된 대기열 아이템
		registerQueue(QUEUE_ID);

		Promotion promotion = queuePromoteUseCase.promote(NOW);

		assertThat(promotion.promoted()).isEqualTo(1);
		assertThat(redisTemplate.opsForZSet().range(ACTIVE_KEY, 0, -1)).containsExactly("7");
	}

	// waiting이면 score가 등록 시각, active면 끝 시각이다
	private void addMember(String key, long memberId, LocalDateTime score) {
		redisTemplate.opsForZSet().add(key, String.valueOf(memberId), epochMillis(score));
	}

	// 등록 Lua가 하듯 스케줄러 목록(queue:waiting)에 대기열 값을 넣는다
	private void registerQueue(String queueId) {
		redisTemplate.opsForSet().add(QUEUES_KEY, queueId);
	}

	// QueueRedisRepository와 같은 방식으로 score(epoch ms)를 만든다
	private static double epochMillis(LocalDateTime time) {
		return time.atZone(ZONE).toInstant().toEpochMilli();
	}
}
