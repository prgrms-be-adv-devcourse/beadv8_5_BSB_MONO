package com.bukang.member.recordcert.out;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.bukang.member.recordcert.domain.exception.RecordCertCheckUnavailableException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 기록증 제출 잠금을 Redis에 둔다 (BUK-274, .claude/rules/redis-key-convention.md)
 * - order-items:{참가권 ID}:record-cert-lock → 잠근 요청의 토큰 (SET NX EX 30)
 * Redis가 응답하지 않으면 잠금 없이 통과하지 않고 503으로 막는다 (11/17, 그동안 중복 제출이 생기지 않게)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisSubmissionLock implements SubmissionLock {
	static final Duration TTL = Duration.ofSeconds(30);
	// 토큰이 같을 때만 지운다. 확인과 지우기 사이에 다른 요청이 잠금을 잡는 틈이 없게 한 번에 한다
	private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>("""
		if redis.call('GET', KEYS[1]) == ARGV[1] then
			return redis.call('DEL', KEYS[1])
		end
		return 0
		""", Long.class);

	private final StringRedisTemplate redisTemplate;

	static String lockKey(long orderItemId) {
		return "order-items:" + orderItemId + ":record-cert-lock";
	}

	@Override
	public Optional<String> tryLock(long orderItemId) {
		String token = UUID.randomUUID().toString();
		Boolean locked;
		try {
			locked = redisTemplate.opsForValue().setIfAbsent(lockKey(orderItemId), token, TTL);
		} catch (DataAccessException exception) {
			log.warn("기록증 제출 잠금을 잡지 못했습니다. orderItemId={}, cause={}", orderItemId, exception.toString());
			throw new RecordCertCheckUnavailableException("기록증 제출을 확인하지 못했습니다. 잠시 뒤 다시 시도해주세요.");
		}
		return Boolean.TRUE.equals(locked) ? Optional.of(token) : Optional.empty();
	}

	// 풀지 못해도 30초 뒤 저절로 풀리므로, 이미 끝난 제출 결과를 실패로 바꾸지 않고 로그만 남긴다
	@Override
	public void unlock(long orderItemId, String token) {
		try {
			redisTemplate.execute(UNLOCK_SCRIPT, List.of(lockKey(orderItemId)), token);
		} catch (DataAccessException exception) {
			log.warn("기록증 제출 잠금을 풀지 못했습니다. 30초 뒤 풀립니다. orderItemId={}, cause={}", orderItemId,
				exception.toString());
		}
	}
}
