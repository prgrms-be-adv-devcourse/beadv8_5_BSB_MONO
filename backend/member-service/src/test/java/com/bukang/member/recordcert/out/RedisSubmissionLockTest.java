package com.bukang.member.recordcert.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.bukang.member.recordcert.domain.exception.RecordCertCheckUnavailableException;
import com.redis.testcontainers.RedisContainer;

// 기록증 제출 잠금을 진짜 Redis 컨테이너로 확인한다 (BUK-274, Docker 필요)
@Testcontainers
class RedisSubmissionLockTest {
	private static final long ORDER_ITEM_ID = 42;

	@Container
	private static final RedisContainer REDIS = new RedisContainer("redis:8.2");

	private static LettuceConnectionFactory connectionFactory;
	private static StringRedisTemplate redisTemplate;
	private static RedisSubmissionLock lock;

	@BeforeAll
	static void connect() {
		connectionFactory = new LettuceConnectionFactory(REDIS.getRedisHost(), REDIS.getRedisPort());
		connectionFactory.afterPropertiesSet();
		connectionFactory.start();
		redisTemplate = new StringRedisTemplate(connectionFactory);
		lock = new RedisSubmissionLock(redisTemplate);
	}

	@AfterAll
	static void disconnect() {
		connectionFactory.destroy();
	}

	@BeforeEach
	void flush() {
		redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
	}

	@Test
	@DisplayName("잠금은 참가권 키에 30초 만료로 걸리고, 잡혀 있는 동안 같은 참가권은 못 잡는다. 다른 참가권은 따로다")
	void lockOnce() {
		Optional<String> token = lock.tryLock(ORDER_ITEM_ID);

		assertThat(token).isPresent();
		assertThat(redisTemplate.getExpire("order-items:42:record-cert-lock")).isBetween(29L, 30L);
		assertThat(lock.tryLock(ORDER_ITEM_ID)).isEmpty();
		assertThat(lock.tryLock(43)).isPresent();
	}

	@Test
	@DisplayName("토큰이 다르면 풀지 않고, 같으면 풀어 다시 잡을 수 있다")
	void unlockOnlyOwnToken() {
		String token = lock.tryLock(ORDER_ITEM_ID).orElseThrow();

		lock.unlock(ORDER_ITEM_ID, "other-token");
		assertThat(lock.tryLock(ORDER_ITEM_ID)).isEmpty();

		lock.unlock(ORDER_ITEM_ID, token);
		assertThat(lock.tryLock(ORDER_ITEM_ID)).isPresent();
	}

	@Test
	@DisplayName("같은 참가권 잠금을 동시에 열 번 잡으면 하나만 잡는다")
	void concurrentLock() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(10);
		try {
			List<Callable<Optional<String>>> tasks = new ArrayList<>();
			for (int i = 0; i < 10; i++) {
				tasks.add(() -> lock.tryLock(ORDER_ITEM_ID));
			}
			long acquired = 0;
			for (Future<Optional<String>> result : executor.invokeAll(tasks)) {
				acquired += result.get().isPresent() ? 1 : 0;
			}
			assertThat(acquired).isEqualTo(1);
		} finally {
			executor.shutdown();
		}
	}

	@Test
	@DisplayName("Redis가 응답하지 않으면 잠금 없이 통과하지 않고 503 예외를 던진다")
	void redisDown() {
		LettuceConnectionFactory downFactory = new LettuceConnectionFactory("localhost", 1);
		downFactory.afterPropertiesSet();
		downFactory.start();
		try {
			RedisSubmissionLock downLock = new RedisSubmissionLock(new StringRedisTemplate(downFactory));

			assertThatThrownBy(() -> downLock.tryLock(ORDER_ITEM_ID))
				.isInstanceOf(RecordCertCheckUnavailableException.class);
		} finally {
			downFactory.destroy();
		}
	}
}
