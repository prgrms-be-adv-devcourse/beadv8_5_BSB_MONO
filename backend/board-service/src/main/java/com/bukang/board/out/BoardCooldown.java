package com.bukang.board.out;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 도배 제한. 글은 1분, 댓글은 10초에 한 번만 쓰게 Redis 키를 걸어 센다 (키는 .claude/rules/redis-key-convention.md)
 * 키는 만료로 저절로 지워진다. Redis가 멈추면 제한 없이 통과시킨다
 * (Redis 하나 때문에 게시판 글쓰기 전체가 멈추는 것보다 잠깐 도배가 가능한 쪽이 피해가 작다)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BoardCooldown {
	private static final Duration POST_COOLDOWN = Duration.ofMinutes(1);
	private static final Duration COMMENT_COOLDOWN = Duration.ofSeconds(10);

	private final StringRedisTemplate redisTemplate;

	// 쓸 수 있으면 true(지금부터 제한 시작), 제한 중이면 false
	public boolean acquirePost(long memberId) {
		return acquire("members:" + memberId + ":board-post-cooldown", POST_COOLDOWN);
	}

	public boolean acquireComment(long memberId) {
		return acquire("members:" + memberId + ":board-comment-cooldown", COMMENT_COOLDOWN);
	}

	private boolean acquire(String key, Duration ttl) {
		try {
			// 키가 없을 때만 만든다. 동시에 온 두 요청 중 하나만 true를 받는다
			return !Boolean.FALSE.equals(redisTemplate.opsForValue().setIfAbsent(key, "1", ttl));
		} catch (RuntimeException failure) {
			log.warn("Redis를 쓰지 못해 도배 제한 없이 통과시킵니다. key={}, cause={}", key, failure.toString());
			return true;
		}
	}
}
