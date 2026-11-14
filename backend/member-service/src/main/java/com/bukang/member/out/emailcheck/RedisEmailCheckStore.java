package com.bukang.member.out.emailcheck;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * 이메일 중복 확인 요청 횟수를 Redis에 둔다 (.claude/rules/redis-key-convention.md)
 * - email-availability-requests:ip:{IP 해시}:count → IP당 요청 수 (기간이 지나면 사라진다)
 */
@Repository
@RequiredArgsConstructor
public class RedisEmailCheckStore implements EmailCheckStore {
	// 올리기와 만료 걸기를 한 번에 해, 사이에 끊겨 만료 없는 키가 남는 일이 없다. 첫 요청에서만 만료를 건다
	private static final DefaultRedisScript<Long> COUNT_SCRIPT = new DefaultRedisScript<>("""
		local count = redis.call('INCR', KEYS[1])
		if count == 1 or redis.call('TTL', KEYS[1]) == -1 then
			redis.call('PEXPIRE', KEYS[1], ARGV[1])
		end
		return count
		""", Long.class);

	private final StringRedisTemplate redisTemplate;

	static String countKey(String ipHash) {
		return "email-availability-requests:ip:" + ipHash + ":count";
	}

	@Override
	public boolean tryAcquire(String ipHash, int limit, Duration window) {
		Long count = redisTemplate.execute(COUNT_SCRIPT, List.of(countKey(ipHash)),
			String.valueOf(window.toMillis()));
		return count != null && count <= limit;
	}
}
