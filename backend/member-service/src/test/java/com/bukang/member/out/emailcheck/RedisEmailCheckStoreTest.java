package com.bukang.member.out.emailcheck;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

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

// Lua로 올리기와 만료를 함께 거는지 진짜 Redis 컨테이너로 확인한다 (Docker 필요)
@Testcontainers
class RedisEmailCheckStoreTest {
	private static final Duration WINDOW = Duration.ofMinutes(1);

	@Container
	private static final RedisContainer REDIS = new RedisContainer("redis:8.2");

	private static LettuceConnectionFactory connectionFactory;
	private static StringRedisTemplate redisTemplate;
	private static RedisEmailCheckStore store;

	@BeforeAll
	static void connect() {
		connectionFactory = new LettuceConnectionFactory(REDIS.getRedisHost(), REDIS.getRedisPort());
		connectionFactory.afterPropertiesSet();
		connectionFactory.start();
		redisTemplate = new StringRedisTemplate(connectionFactory);
		store = new RedisEmailCheckStore(redisTemplate);
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
	@DisplayName("한도까지는 받고 다음 요청부터 막는다. 다른 IP는 따로 센다")
	void limitsPerIp() {
		for (int i = 0; i < 10; i++) {
			assertThat(store.tryAcquire("ip-1", 10, WINDOW)).isTrue();
		}
		assertThat(store.tryAcquire("ip-1", 10, WINDOW)).isFalse();

		assertThat(store.tryAcquire("ip-2", 10, WINDOW)).isTrue();
	}

	@Test
	@DisplayName("키 형식은 email-availability-requests:ip:{IP 해시}:count이고 만료가 걸린다")
	void keyFormatAndExpiry() {
		store.tryAcquire("ip-1", 10, WINDOW);

		assertThat(RedisEmailCheckStore.countKey("ip-1")).isEqualTo("email-availability-requests:ip:ip-1:count");
		assertThat(redisTemplate.getExpire(RedisEmailCheckStore.countKey("ip-1"))).isBetween(1L, 60L);
	}

	@Test
	@DisplayName("만료가 지나면 횟수가 0부터 다시 시작해 다시 받는다")
	void releasesAfterWindow() {
		for (int i = 0; i < 11; i++) {
			store.tryAcquire("ip-1", 10, WINDOW);
		}

		redisTemplate.delete(RedisEmailCheckStore.countKey("ip-1"));

		assertThat(store.tryAcquire("ip-1", 10, WINDOW)).isTrue();
	}

	@Test
	@DisplayName("만료 없이 남은 키도 다음 요청에서 만료를 다시 건다")
	void restoresMissingExpiry() {
		String key = RedisEmailCheckStore.countKey("ip-1");
		redisTemplate.opsForValue().set(key, "3");

		store.tryAcquire("ip-1", 10, WINDOW);

		assertThat(redisTemplate.getExpire(key)).isPositive();
	}
}
