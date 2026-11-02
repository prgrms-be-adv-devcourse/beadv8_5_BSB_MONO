package com.bukang.member.out.identity;

import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import com.bukang.common.global.json.JsonConverter;

import lombok.RequiredArgsConstructor;

/**
 * 본인인증 상태를 Redis에 둔다. 키는 identity-verifications:{인증 ID}:{종류} (.claude/rules/redis-key-convention.md)
 * 인증 ID는 서버가 만든 추측할 수 없는 값이라 키를 알아도 남의 결과를 꺼낼 수 없다
 */
@Repository
@RequiredArgsConstructor
public class RedisIdentityVerificationStore implements IdentityVerificationStore {
	// 인증 대기·결과는 소셜 가입 대기와 같은 15분이다. 사용자가 알아야 할 시간 제한을 하나로 맞춘다
	static final Duration PENDING_TTL = Duration.ofMinutes(15);
	static final Duration VERIFIED_TTL = Duration.ofMinutes(15);
	private static final String PENDING_VALUE = "1";

	private final StringRedisTemplate redisTemplate;
	private final JsonConverter jsonConverter;

	static String pendingKey(String identityVerificationId) {
		return "identity-verifications:" + identityVerificationId + ":pending";
	}

	static String verifiedKey(String identityVerificationId) {
		return "identity-verifications:" + identityVerificationId + ":verified";
	}

	@Override
	public void savePending(String identityVerificationId) {
		redisTemplate.opsForValue().set(pendingKey(identityVerificationId), PENDING_VALUE, PENDING_TTL);
	}

	@Override
	public boolean isPending(String identityVerificationId) {
		return Boolean.TRUE.equals(redisTemplate.hasKey(pendingKey(identityVerificationId)));
	}

	@Override
	public void saveVerified(String identityVerificationId, VerifiedIdentity identity) {
		redisTemplate.opsForValue()
			.set(verifiedKey(identityVerificationId), jsonConverter.toJson(identity), VERIFIED_TTL);
		redisTemplate.delete(pendingKey(identityVerificationId));
	}

	@Override
	public Optional<VerifiedIdentity> findVerified(String identityVerificationId) {
		return Optional.ofNullable(redisTemplate.opsForValue().get(verifiedKey(identityVerificationId)))
			.map(json -> jsonConverter.fromJson(json, VerifiedIdentity.class));
	}

	@Override
	public void deleteVerified(String identityVerificationId) {
		redisTemplate.delete(verifiedKey(identityVerificationId));
	}
}
