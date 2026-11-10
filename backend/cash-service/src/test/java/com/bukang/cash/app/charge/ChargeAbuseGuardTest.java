package com.bukang.cash.app.charge;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

// 시도·실패 횟수는 INCR 전에 SET NX EX로 타이머부터 건다. 순서가 반대(INCR 먼저, count==1일 때만 EXPIRE)면
// EXPIRE만 실패해도 타이머 없는 카운터가 영구히 남을 수 있다(PR #385 리뷰에서 지적됨)
class ChargeAbuseGuardTest {
	private static final long CUSTOMER_ID = 7;

	@SuppressWarnings("unchecked")
	@Test
	@DisplayName("시도 횟수(INCR)가 실패해도 타이머(SET NX EX)는 이미 걸려 있다")
	void setsExpiryBeforeIncrementingAttempts() {
		StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
		ValueOperations<String, String> valueOps = mock(ValueOperations.class);
		given(redisTemplate.opsForValue()).willReturn(valueOps);
		String attemptsKey = "charge:" + CUSTOMER_ID + ":attempts";
		willThrow(new QueryTimeoutException("Redis 타임아웃")).given(valueOps).increment(attemptsKey);

		ChargeAbuseGuard guard = new ChargeAbuseGuard(redisTemplate);

		// 부가 안전장치라 fail-open — INCR이 실패해도 밖으로 던지지 않고 조용히 넘어가야 한다
		assertThatCode(() -> guard.registerNewAttempt(CUSTOMER_ID)).doesNotThrowAnyException();

		// INCR이 실패했어도 타이머(SET NX EX)는 그보다 먼저 걸렸어야 한다
		InOrder order = inOrder(valueOps);
		order.verify(valueOps).setIfAbsent(attemptsKey, "0", Duration.ofMinutes(5));
		order.verify(valueOps).increment(attemptsKey);
	}

	@SuppressWarnings("unchecked")
	@Test
	@DisplayName("결제 실패 횟수(INCR)가 실패해도 타이머(SET NX EX)는 이미 걸려 있다")
	void setsExpiryBeforeIncrementingFailures() {
		StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
		ValueOperations<String, String> valueOps = mock(ValueOperations.class);
		given(redisTemplate.opsForValue()).willReturn(valueOps);
		String failuresKey = "charge:" + CUSTOMER_ID + ":failures";
		willThrow(new QueryTimeoutException("Redis 타임아웃")).given(valueOps).increment(failuresKey);

		ChargeAbuseGuard guard = new ChargeAbuseGuard(redisTemplate);

		assertThatCode(() -> guard.registerDefiniteFailure(CUSTOMER_ID)).doesNotThrowAnyException();

		InOrder order = inOrder(valueOps);
		order.verify(valueOps).setIfAbsent(failuresKey, "0", Duration.ofHours(1));
		order.verify(valueOps).increment(failuresKey);
	}
}
