package com.bukang.member.out.passwordreset;

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

// SET NX·GETDEL·만료를 진짜 Redis 컨테이너로 확인한다 (Docker 필요)
@Testcontainers
class RedisPasswordResetStoreTest {
	private static final long MEMBER_ID = 7L;
	private static final Duration TTL = Duration.ofMinutes(30);
	private static final Duration COOLDOWN = Duration.ofMinutes(1);
	private static final Duration WINDOW = Duration.ofHours(24);

	@Container
	private static final RedisContainer REDIS = new RedisContainer("redis:8.2");

	private static LettuceConnectionFactory connectionFactory;
	private static StringRedisTemplate redisTemplate;
	private static RedisPasswordResetStore store;

	@BeforeAll
	static void connect() {
		connectionFactory = new LettuceConnectionFactory(REDIS.getRedisHost(), REDIS.getRedisPort());
		connectionFactory.afterPropertiesSet();
		connectionFactory.start();
		redisTemplate = new StringRedisTemplate(connectionFactory);
		store = new RedisPasswordResetStore(redisTemplate);
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
	@DisplayName("토큰은 30분 만료로 저장하고, 꺼내면 지워져 두 번째는 비어 있다")
	void takeDeletesToken() {
		store.saveToken(MEMBER_ID, "hash-1", TTL);

		assertThat(redisTemplate.getExpire(RedisPasswordResetStore.tokenKey("hash-1"))).isPositive();
		assertThat(store.findMemberId("hash-1")).contains(MEMBER_ID);
		assertThat(store.takeMemberId("hash-1")).contains(MEMBER_ID);
		assertThat(store.takeMemberId("hash-1")).isEmpty();
	}

	@Test
	@DisplayName("새 토큰을 저장하면 이전 토큰을 지우고 최신 토큰만 남긴다")
	void newTokenReplacesPrevious() {
		store.saveToken(MEMBER_ID, "hash-1", TTL);
		store.saveToken(MEMBER_ID, "hash-2", TTL);

		assertThat(store.findMemberId("hash-1")).isEmpty();
		assertThat(store.isLatestToken(MEMBER_ID, "hash-1")).isFalse();
		assertThat(store.isLatestToken(MEMBER_ID, "hash-2")).isTrue();
		assertThat(redisTemplate.getExpire(RedisPasswordResetStore.latestTokenKey(MEMBER_ID))).isPositive();
	}

	@Test
	@DisplayName("같은 이메일은 1분 안에 한 번만 받고, 하루 횟수를 넘으면 막는다")
	void limitsRequests() {
		assertThat(store.tryAcquireRequest("email-1", COOLDOWN, 2, WINDOW)).isTrue();
		assertThat(store.tryAcquireRequest("email-1", COOLDOWN, 2, WINDOW)).isFalse();

		redisTemplate.delete(RedisPasswordResetStore.cooldownKey("email-1"));
		assertThat(store.tryAcquireRequest("email-1", COOLDOWN, 2, WINDOW)).isTrue();
		redisTemplate.delete(RedisPasswordResetStore.cooldownKey("email-1"));
		assertThat(store.tryAcquireRequest("email-1", COOLDOWN, 2, WINDOW)).isFalse();

		assertThat(redisTemplate.getExpire(RedisPasswordResetStore.dailyCountKey("email-1"))).isPositive();
		assertThat(store.tryAcquireRequest("email-2", COOLDOWN, 2, WINDOW)).isTrue();
	}

	@Test
	@DisplayName("같은 IP는 하루 횟수를 넘으면 막고, 키에 만료를 건다")
	void limitsRequestsPerIp() {
		assertThat(store.tryAcquireIpRequest("ip-1", 2, WINDOW)).isTrue();
		assertThat(store.tryAcquireIpRequest("ip-1", 2, WINDOW)).isTrue();
		assertThat(store.tryAcquireIpRequest("ip-1", 2, WINDOW)).isFalse();

		assertThat(redisTemplate.getExpire(RedisPasswordResetStore.ipDailyCountKey("ip-1"))).isPositive();
		assertThat(store.tryAcquireIpRequest("ip-2", 2, WINDOW)).isTrue();
	}
}
