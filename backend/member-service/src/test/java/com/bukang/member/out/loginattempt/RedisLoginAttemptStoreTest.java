package com.bukang.member.out.loginattempt;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.redis.testcontainers.RedisContainer;

// Lua 스크립트의 올리기·만료 걸기를 진짜 Redis 컨테이너로 확인한다 (Docker 필요)
@Testcontainers
class RedisLoginAttemptStoreTest {
	private static final String HASH = "hash-1";
	private static final String KEY = RedisLoginAttemptStore.failCountKey(HASH);
	private static final int MAX = 5;
	private static final Duration BLOCK = Duration.ofMinutes(10);

	@Container
	private static final RedisContainer REDIS = new RedisContainer("redis:8.2");

	private static LettuceConnectionFactory connectionFactory;
	private static StringRedisTemplate redisTemplate;
	private static RedisLoginAttemptStore store;

	@BeforeAll
	static void connect() {
		connectionFactory = new LettuceConnectionFactory(REDIS.getRedisHost(), REDIS.getRedisPort());
		connectionFactory.afterPropertiesSet();
		connectionFactory.start();
		redisTemplate = new StringRedisTemplate(connectionFactory);
		store = new RedisLoginAttemptStore(redisTemplate);
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
	@DisplayName("시도마다 1씩 올라가고, 처음 센 키에 10분 만료가 걸린다")
	void countsUpAndSetsExpiryOnFirst() {
		assertThat(store.record(HASH, MAX, BLOCK)).isEqualTo(1);
		assertThat(store.record(HASH, MAX, BLOCK)).isEqualTo(2);

		assertThat(redisTemplate.getExpire(KEY)).isBetween(1L, BLOCK.toSeconds());
	}

	@Test
	@DisplayName("5번째에서 만료를 다시 10분으로 맞추고, 그 뒤 시도는 만료를 늘리지 않는다")
	void resetsExpiryAtMaxFailuresOnly() {
		for (int i = 1; i < MAX; i++) {
			store.record(HASH, MAX, BLOCK);
		}
		redisTemplate.expire(KEY, Duration.ofSeconds(100));

		store.record(HASH, MAX, BLOCK);
		assertThat(redisTemplate.getExpire(KEY)).isGreaterThan(500);

		redisTemplate.expire(KEY, Duration.ofSeconds(100));
		assertThat(store.record(HASH, MAX, BLOCK)).isEqualTo(MAX + 1);
		assertThat(redisTemplate.getExpire(KEY)).isLessThanOrEqualTo(100);
	}

	@Test
	@DisplayName("만료가 없는 키가 남아 있으면 다음 시도에서 만료를 건다")
	void repairsKeyWithoutExpiry() {
		redisTemplate.opsForValue().set(KEY, "2");

		assertThat(store.record(HASH, MAX, BLOCK)).isEqualTo(3);

		assertThat(redisTemplate.getExpire(KEY)).isPositive();
	}

	@Test
	@DisplayName("차단 시간이 지나면 키가 사라져 처음부터 다시 센다")
	void expiresAfterBlockTime() throws InterruptedException {
		Duration shortBlock = Duration.ofMillis(300);
		for (int i = 0; i < MAX + 1; i++) {
			store.record(HASH, MAX, shortBlock);
		}

		Thread.sleep(500);

		assertThat(store.record(HASH, MAX, shortBlock)).isEqualTo(1);
	}

	@Test
	@DisplayName("지우면 키가 없어진다")
	void clearDeletesKey() {
		store.record(HASH, MAX, BLOCK);

		store.clear(HASH);

		assertThat(redisTemplate.hasKey(KEY)).isFalse();
		assertThat(store.record(HASH, MAX, BLOCK)).isEqualTo(1);
	}

	@Test
	@DisplayName("키에는 해시만 들어가고 login-attempts:{해시}:failCount 형식이다")
	void keyFormat() {
		assertThat(KEY).isEqualTo("login-attempts:hash-1:failCount");
	}

	@Test
	@DisplayName("동시에 20번 시도해도 횟수가 겹치지 않아, 5 이하로 받는 요청은 정확히 5개다")
	void concurrentAttemptsGetDistinctCounts() throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(10);
		try {
			List<Callable<Long>> tasks = IntStream.range(0, 20)
				.<Callable<Long>>mapToObj(i -> () -> store.record(HASH, MAX, BLOCK))
				.toList();
			List<Long> counts = pool.invokeAll(tasks).stream().map(RedisLoginAttemptStoreTest::get).toList();

			assertThat(counts).doesNotHaveDuplicates().allMatch(count -> count >= 1 && count <= 20);
			assertThat(counts.stream().filter(count -> count <= MAX)).hasSize(MAX);
			assertThat(redisTemplate.getExpire(KEY)).isPositive();
		} finally {
			pool.shutdownNow();
		}
	}

	private static Long get(Future<Long> future) {
		try {
			return future.get();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
}
