package com.bukang.member.out.passwordreset;

import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * 비밀번호 재설정 상태를 Redis에 둔다 (.claude/rules/redis-key-convention.md)
 * - password-resets:{토큰 해시}:memberId → 회원 ID (링크 유효 시간)
 * - members:{회원 ID}:passwordReset → 지금 유효한 토큰 해시 (새 링크를 만들 때 이전 링크를 지운다)
 * - password-reset-requests:{이메일 해시}:cooldown, :dailyCount → 이메일당 요청 제한
 * 이메일은 해시로만 키에 넣어 Redis에 이메일 원문이 남지 않는다
 */
@Repository
@RequiredArgsConstructor
public class RedisPasswordResetStore implements PasswordResetStore {
	private static final String COOLDOWN_VALUE = "1";

	private final StringRedisTemplate redisTemplate;

	static String tokenKey(String tokenHash) {
		return "password-resets:" + tokenHash + ":memberId";
	}

	static String latestTokenKey(long memberId) {
		return "members:" + memberId + ":passwordReset";
	}

	static String cooldownKey(String emailHash) {
		return "password-reset-requests:" + emailHash + ":cooldown";
	}

	static String dailyCountKey(String emailHash) {
		return "password-reset-requests:" + emailHash + ":dailyCount";
	}

	@Override
	public boolean tryAcquireRequest(String emailHash, Duration cooldown, int dailyLimit, Duration window) {
		// SET NX로 1분 안의 두 번째 요청을 막는다. 두 요청이 동시에 와도 하나만 키를 만든다
		Boolean first = redisTemplate.opsForValue().setIfAbsent(cooldownKey(emailHash), COOLDOWN_VALUE, cooldown);
		if (!Boolean.TRUE.equals(first)) {
			return false;
		}
		String dailyKey = dailyCountKey(emailHash);
		Long count = redisTemplate.opsForValue().increment(dailyKey);
		// 처음 센 요청에서만 만료를 건다. 기간은 첫 요청부터 센다
		if (count != null && count == 1) {
			redisTemplate.expire(dailyKey, window);
		}
		return count != null && count <= dailyLimit;
	}

	@Override
	public void saveToken(long memberId, String tokenHash, Duration ttl) {
		redisTemplate.opsForValue().set(tokenKey(tokenHash), String.valueOf(memberId), ttl);
		String previous = redisTemplate.opsForValue().getAndSet(latestTokenKey(memberId), tokenHash);
		redisTemplate.expire(latestTokenKey(memberId), ttl);
		if (previous != null && !previous.equals(tokenHash)) {
			redisTemplate.delete(tokenKey(previous));
		}
	}

	@Override
	public boolean isLatestToken(long memberId, String tokenHash) {
		return tokenHash.equals(redisTemplate.opsForValue().get(latestTokenKey(memberId)));
	}

	@Override
	public Optional<Long> findMemberId(String tokenHash) {
		return Optional.ofNullable(redisTemplate.opsForValue().get(tokenKey(tokenHash))).map(Long::valueOf);
	}

	// GETDEL 한 명령으로 꺼내고 지운다
	@Override
	public Optional<Long> takeMemberId(String tokenHash) {
		return Optional.ofNullable(redisTemplate.opsForValue().getAndDelete(tokenKey(tokenHash))).map(Long::valueOf);
	}

	@Override
	public void deleteLatestToken(long memberId) {
		redisTemplate.delete(latestTokenKey(memberId));
	}
}
