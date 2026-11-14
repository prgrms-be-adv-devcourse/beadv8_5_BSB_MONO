package com.bukang.member.out.payoutaccount;

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

// 계좌 확인 횟수 제한을 진짜 Redis로 확인한다. Docker가 없으면 건너뛴다
@Testcontainers(disabledWithoutDocker = true)
class RedisPayoutAccountVerifyLimiterTest {
	private static final long MEMBER_ID = 7L;

	@Container
	private static final RedisContainer REDIS = new RedisContainer("redis:8.2");

	private static LettuceConnectionFactory connectionFactory;
	private static StringRedisTemplate redisTemplate;
	private static RedisPayoutAccountVerifyLimiter limiter;

	@BeforeAll
	static void connect() {
		connectionFactory = new LettuceConnectionFactory(REDIS.getRedisHost(), REDIS.getRedisPort());
		connectionFactory.afterPropertiesSet();
		connectionFactory.start();
		redisTemplate = new StringRedisTemplate(connectionFactory);
		limiter = new RedisPayoutAccountVerifyLimiter(redisTemplate, 5, Duration.ofHours(24));
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
	@DisplayName("회원 한 명당 다섯 번까지 받고 여섯 번째는 막으며, 키에는 만료가 걸린다")
	void limitsFivePerMember() {
		for (int i = 0; i < 5; i++) {
			assertThat(limiter.tryAcquire(MEMBER_ID)).isTrue();
		}

		assertThat(limiter.tryAcquire(MEMBER_ID)).isFalse();
		assertThat(limiter.tryAcquire(MEMBER_ID + 1)).isTrue();
		assertThat(RedisPayoutAccountVerifyLimiter.countKey(MEMBER_ID))
			.isEqualTo("members:7:payoutAccountVerifyCount");
		assertThat(redisTemplate.getExpire(RedisPayoutAccountVerifyLimiter.countKey(MEMBER_ID))).isPositive();
	}

	@Test
	@DisplayName("돌려준 한 번은 다시 쓸 수 있다")
	void releaseGivesBackOne() {
		for (int i = 0; i < 5; i++) {
			limiter.tryAcquire(MEMBER_ID);
		}

		limiter.release(MEMBER_ID);

		assertThat(limiter.tryAcquire(MEMBER_ID)).isTrue();
		assertThat(limiter.tryAcquire(MEMBER_ID)).isFalse();
	}
}
