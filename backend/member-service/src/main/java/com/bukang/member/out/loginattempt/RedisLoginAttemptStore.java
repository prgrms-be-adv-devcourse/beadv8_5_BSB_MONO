package com.bukang.member.out.loginattempt;

import java.time.Duration;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * 로그인 시도 횟수를 Redis에 둔다 (.claude/rules/redis-key-convention.md)
 * - login-attempts:{이메일 해시}:failCount → 시도 횟수 (차단 시간만큼 만료)
 * 이메일은 해시로만 키에 넣어 Redis에 이메일 원문이 남지 않는다
 */
@Repository
@RequiredArgsConstructor
public class RedisLoginAttemptStore implements LoginAttemptStore {
	private static final RedisScript<Long> RECORD = script();

	private final StringRedisTemplate redisTemplate;

	static String failCountKey(String emailHash) {
		return "login-attempts:" + emailHash + ":failCount";
	}

	@Override
	public long record(String emailHash, int maxFailures, Duration blockTime) {
		Long count = redisTemplate.execute(RECORD, List.of(failCountKey(emailHash)),
			String.valueOf(maxFailures), String.valueOf(blockTime.toMillis()));
		return count == null ? 0 : count;
	}

	@Override
	public void clear(String emailHash) {
		redisTemplate.delete(failCountKey(emailHash));
	}

	private static RedisScript<Long> script() {
		DefaultRedisScript<Long> script = new DefaultRedisScript<>();
		script.setLocation(new ClassPathResource("redis/login-attempt-record.lua"));
		script.setResultType(Long.class);
		return script;
	}
}
