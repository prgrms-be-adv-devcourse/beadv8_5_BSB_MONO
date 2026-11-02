package com.bukang.queue.support;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.bukang.common.global.json.JsonConverter;
import com.bukang.queue.TestcontainersConfiguration;
import com.bukang.queue.domain.QueueRace;
import com.bukang.queue.domain.QueueRaceCourse;
import com.bukang.queue.out.QueueRaceCourseRepository;
import com.bukang.queue.out.QueueRaceRepository;

/**
 * queue-service 통합 테스트의 공통 설정. 모든 테스트 클래스가 이 클래스를 상속해 같은 Spring 컨텍스트와 Redis 컨테이너를 함께 쓴다.
 * DB는 H2(test 프로파일), Redis는 컨테이너(Docker가 없으면 실패한다)이고, Kafka 리스너는 자동 시작하지 않는다
 * (메시지는 리스너 메서드를 직접 불러 넣는다).
 * 테스트가 끝나면 대회·종목 복제본과 Redis를 비운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class QueueIntegrationTest {
	@Autowired
	protected JsonConverter jsonConverter;
	@Autowired
	protected QueueRaceRepository queueRaceRepository;
	@Autowired
	protected QueueRaceCourseRepository queueRaceCourseRepository;
	@Autowired
	protected StringRedisTemplate redisTemplate;

	@AfterEach
	void cleanUpQueueTest() {
		queueRaceRepository.deleteAll();
		queueRaceCourseRepository.deleteAll();
		redisTemplate.execute((RedisCallback<Void>)connection -> {
			connection.serverCommands().flushDb();
			return null;
		});
	}

	// 메시지의 수정 일시. 같은 날(2026-10-15)의 시각만 바꿔 어느 메시지가 먼저인지 나타낸다
	protected static LocalDateTime at(int hour) {
		return LocalDateTime.of(2026, 10, 15, hour, 0);
	}

	protected QueueRace findRace(long raceId) {
		return queueRaceRepository.findById(raceId).orElseThrow();
	}

	protected QueueRaceCourse findCourse(long raceCourseId) {
		return queueRaceCourseRepository.findById(raceCourseId).orElseThrow();
	}
}
