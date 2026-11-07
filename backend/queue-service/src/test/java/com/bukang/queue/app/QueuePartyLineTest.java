package com.bukang.queue.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.bukang.common.shared.race.domain.RaceSaleStatus;
import com.bukang.queue.config.QueueProperties;
import com.bukang.queue.domain.QueueRace;
import com.bukang.queue.domain.QueueRaceCourse;
import com.bukang.queue.domain.exception.QueueNotEnterableException;
import com.bukang.queue.support.QueueIntegrationTest;

/**
 * 파티 대기열(파티 줄)을 실제 Redis 컨테이너로 확인한다.
 * 줄 상태는 앞 흐름(등록 API)을 거치지 않고 Redis에 직접 넣고, 시각은 고정 시각(NOW)을 UseCase에 넣는다.
 */
class QueuePartyLineTest extends QueueIntegrationTest {
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 16, 14, 0);
	private static final long RACE_ID = 1;
	private static final long RACE_COURSE_ID = 11;
	private static final long LEADER_MEMBER_ID = 7;
	private static final String PARTY_WAITING_KEY = "race:1:racecourse:11:party:waiting";
	private static final String PARTY_QUEUES_KEY = "queue:party:waiting";
	// QueueRedisRepository가 score(epoch ms)를 만들 때 쓰는 시간대
	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	@Autowired
	private QueuePromoteUseCase queuePromoteUseCase;
	@Autowired
	private QueueAdmissionUseCase queueAdmissionUseCase;
	@Autowired
	private QueueRegisterUseCase queueRegisterUseCase;
	@Autowired
	private QueueProperties queueProperties;

	@Test
	@DisplayName("승격한 파티장은 파티 활성 시간(queue.party.active.time)이 지나면 입장 중이 아니다")
	void partyLeaderAdmissionExpiresAfterActiveTime() {
		// given: 파티장이 파티 줄에 서 있고, NOW에 승격된다
		redisTemplate.opsForZSet().add(PARTY_WAITING_KEY, String.valueOf(LEADER_MEMBER_ID),
			epochMillis(NOW.minusMinutes(1)));
		redisTemplate.opsForSet().add(PARTY_QUEUES_KEY, RACE_ID + ":" + RACE_COURSE_ID);
		queuePromoteUseCase.promote(NOW);
		LocalDateTime activeUntil = NOW.plus(queueProperties.party().active().time());

		// when
		boolean admittedJustBefore = queueAdmissionUseCase
			.getPartyAdmission(LEADER_MEMBER_ID, RACE_ID, RACE_COURSE_ID, activeUntil.minusSeconds(1)).isAdmitted();
		boolean admittedAtEnd = queueAdmissionUseCase
			.getPartyAdmission(LEADER_MEMBER_ID, RACE_ID, RACE_COURSE_ID, activeUntil).isAdmitted();

		// then
		assertSoftly(softly -> {
			softly.assertThat(admittedJustBefore).as("끝 시각 1초 전").isTrue();
			softly.assertThat(admittedAtEnd).as("끝 시각").isFalse();
		});
	}

	@Test
	@DisplayName("다른 대회의 종목으로 파티 대기열에 등록하면 403(이 대회의 종목이 아닙니다)이고 파티 줄에 서지 않는다")
	void rejectCourseOfOtherRace() {
		// given: 대회 1은 지금 크루 예매 중이고, 종목 21은 대회 2의 종목이다
		long otherRaceCourseId = 21;
		queueRaceRepository.save(new QueueRace(RACE_ID, RaceSaleStatus.ON_SALE, NOW.plusDays(5), NOW.plusDays(20),
			NOW.minusHours(1), NOW.plusDays(2), at(10)));
		queueRaceCourseRepository.save(new QueueRaceCourse(otherRaceCourseId, 2, at(10), false));

		// when, then
		assertThatThrownBy(() -> queueRegisterUseCase.registerParty(LEADER_MEMBER_ID, RACE_ID, otherRaceCourseId, NOW))
			.isInstanceOf(QueueNotEnterableException.class)
			.hasMessage("이 대회의 종목이 아닙니다.");
		assertThat(redisTemplate.opsForSet().size(PARTY_QUEUES_KEY)).isZero();
	}

	// QueueRedisRepository와 같은 방식으로 score(epoch ms)를 만든다
	private static double epochMillis(LocalDateTime time) {
		return time.atZone(ZONE).toInstant().toEpochMilli();
	}
}
