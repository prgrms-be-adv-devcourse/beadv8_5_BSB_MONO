package com.bukang.queue.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.bukang.common.shared.race.domain.RaceSaleStatus;
import com.bukang.queue.domain.QueueRace;
import com.bukang.queue.domain.QueueRaceCourse;
import com.bukang.queue.dto.QueueStatusDto;
import com.bukang.queue.support.QueueIntegrationTest;

/**
 * 대기열 입장(active) 해제를 실제 Redis 컨테이너로 확인한다.
 * 입장한 회원과 줄에 선 회원은 앞 흐름(등록·승격)을 거치지 않고 Redis에 직접 넣고, 시각은 고정 시각(NOW)을 UseCase에 넣는다.
 */
class QueueReleaseUseCaseTest extends QueueIntegrationTest {
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 16, 14, 0);
	private static final long RACE_ID = 1;
	private static final long RACE_COURSE_ID = 11;
	private static final long MEMBER_ID = 7;
	private static final String ACTIVE_KEY = "race:1:racecourse:11:active";
	private static final String WAITING_KEY = "race:1:racecourse:11:waiting";
	private static final String PARTY_ACTIVE_KEY = "race:1:racecourse:11:party:active";
	private static final String PARTY_WAITING_KEY = "race:1:racecourse:11:party:waiting";
	// QueueRedisRepository가 score(epoch ms)를 만들 때 쓰는 시간대
	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	@Autowired
	private QueueReleaseUseCase queueReleaseUseCase;
	@Autowired
	private QueueRegisterUseCase queueRegisterUseCase;

	@Test
	@DisplayName("결제 화면을 벗어나 입장을 해제한 회원이 다시 개인 대기열에 등록하면, 기다리던 2명 뒤인 3번에 선다")
	void releasedMemberRegistersAtEndOfLine() {
		// given: 회원 7은 입장 중(10분 남음)이고, 줄에는 회원 8·9가 기다린다
		saveRaceInIndividualSale();
		redisTemplate.opsForZSet().add(ACTIVE_KEY, String.valueOf(MEMBER_ID), epochMillis(NOW.plusMinutes(10)));
		redisTemplate.opsForZSet().add(WAITING_KEY, "8", epochMillis(NOW.minusMinutes(2)));
		redisTemplate.opsForZSet().add(WAITING_KEY, "9", epochMillis(NOW.minusMinutes(1)));

		// when
		queueReleaseUseCase.release(MEMBER_ID, RACE_ID, RACE_COURSE_ID);
		QueueStatusDto registered = queueRegisterUseCase.register(MEMBER_ID, RACE_ID, RACE_COURSE_ID, NOW);

		// then
		assertThat(registered.getMyRank()).isEqualTo(3);
	}

	@Test
	@DisplayName("크루 [대회 참가] 화면을 벗어나 입장을 해제한 파티장이 다시 파티 대기열에 등록하면, 기다리던 2팀 뒤인 3번에 선다")
	void releasedPartyLeaderRegistersAtEndOfPartyLine() {
		// given: 파티장 7은 파티 줄에서 입장 중(10분 남음)이고, 파티 줄에는 파티장 8·9가 기다린다
		saveRaceInCrewSale();
		redisTemplate.opsForZSet().add(PARTY_ACTIVE_KEY, String.valueOf(MEMBER_ID), epochMillis(NOW.plusMinutes(10)));
		redisTemplate.opsForZSet().add(PARTY_WAITING_KEY, "8", epochMillis(NOW.minusMinutes(2)));
		redisTemplate.opsForZSet().add(PARTY_WAITING_KEY, "9", epochMillis(NOW.minusMinutes(1)));

		// when
		queueReleaseUseCase.releaseParty(MEMBER_ID, RACE_ID, RACE_COURSE_ID);
		QueueStatusDto registered = queueRegisterUseCase.registerParty(MEMBER_ID, RACE_ID, RACE_COURSE_ID, NOW);

		// then
		assertThat(registered.getMyRank()).isEqualTo(3);
	}

	// NOW에 개인 예매 중인 대회 1(1시간 전 오픈, 하루 뒤 접수 마감, 크루 예매 없음)과 그 종목 11
	private void saveRaceInIndividualSale() {
		queueRaceRepository.save(new QueueRace(RACE_ID, RaceSaleStatus.ON_SALE, NOW.minusHours(1), NOW.plusDays(1),
			null, null, at(10)));
		queueRaceCourseRepository.save(new QueueRaceCourse(RACE_COURSE_ID, RACE_ID, at(10), false));
	}

	// NOW에 크루 예매 중인 대회 1(1시간 전 시작, 이틀 뒤 마감, 개인 예매는 닷새 뒤)과 그 종목 11
	private void saveRaceInCrewSale() {
		queueRaceRepository.save(new QueueRace(RACE_ID, RaceSaleStatus.ON_SALE, NOW.plusDays(5), NOW.plusDays(20),
			NOW.minusHours(1), NOW.plusDays(2), at(10)));
		queueRaceCourseRepository.save(new QueueRaceCourse(RACE_COURSE_ID, RACE_ID, at(10), false));
	}

	// QueueRedisRepository와 같은 방식으로 score(epoch ms)를 만든다
	private static double epochMillis(LocalDateTime time) {
		return time.atZone(ZONE).toInstant().toEpochMilli();
	}
}
