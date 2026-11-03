package com.bukang.member.out.payoutaccount;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 계좌 확인 횟수를 Redis에 센다 (.claude/rules/redis-key-convention.md)
 * - members:{회원 ID}:payoutAccountVerifyCount → 첫 확인부터 기간(하루) 동안의 확인 횟수
 * Redis가 응답하지 않으면 예외가 그대로 나가 확인하지 않는다
 */
public class RedisPayoutAccountVerifyLimiter implements PayoutAccountVerifyLimiter {
	// Redis TTL 명령은 키는 있는데 만료가 없으면 -1을 준다
	private static final long NO_EXPIRE = -1L;

	private final StringRedisTemplate redisTemplate;
	private final int dailyLimit;
	private final Duration window;

	public RedisPayoutAccountVerifyLimiter(StringRedisTemplate redisTemplate, int dailyLimit, Duration window) {
		this.redisTemplate = redisTemplate;
		this.dailyLimit = dailyLimit;
		this.window = window;
	}

	static String countKey(long memberId) {
		return "members:" + memberId + ":payoutAccountVerifyCount";
	}

	// RedisPasswordResetStore.countWithin과 같은 방식이다
	@Override
	public boolean tryAcquire(long memberId) {
		String key = countKey(memberId);
		Long count = redisTemplate.opsForValue().increment(key);
		// 처음 센 요청에서 만료를 건다. 만료 걸기가 실패해 만료 없는 키가 남으면 영원히 막히므로 다음 요청에서 다시 건다
		if (count != null && (count == 1 || Long.valueOf(NO_EXPIRE).equals(redisTemplate.getExpire(key)))) {
			redisTemplate.expire(key, window);
		}
		return count != null && count <= dailyLimit;
	}

	@Override
	public void release(long memberId) {
		redisTemplate.opsForValue().decrement(countKey(memberId));
	}
}
