package com.bukang.member.out;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * 로그아웃한 Access Token 표시 저장소. 게이트웨이가 요청마다 이 키가 있는지 보고 있으면 401로 막는다.
 * 토큰이 어차피 만료되는 때까지만 기억하도록 남은 시간을 TTL로 둔다.
 */
@Repository
@RequiredArgsConstructor
public class RevokedAccessTokenRepository {
	private final StringRedisTemplate redisTemplate;

	// 키 형식: access-tokens:{jti}:revoked (.claude/rules/redis-key-convention.md)
	// 게이트웨이(JwtAuthenticationFilter)가 같은 형식을 읽는다. gateway는 common을 쓰지 않아 상수를 나눠 두고, 양쪽 테스트가 같은 값을 확인한다
	static String key(String jti) {
		return "access-tokens:" + jti + ":revoked";
	}

	public void revoke(String jti, Duration ttl) {
		redisTemplate.opsForValue().set(key(jti), "1", ttl);
	}
}
