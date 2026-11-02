package com.bukang.member.out.oauth2;

import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import com.bukang.common.global.json.JsonConverter;

import lombok.RequiredArgsConstructor;

/**
 * 소셜 로그인 상태를 Redis에 JSON 문자열로 둔다. 키는 oauth2:{임의 ID}:{종류} (.claude/rules/redis-key-convention.md)
 * 임의 ID는 브라우저 쿠키나 주소에만 실리고, 추측할 수 없는 값이라 키를 알아도 남의 상태를 꺼낼 수 없다
 */
@Repository
@RequiredArgsConstructor
public class RedisSocialLoginStore implements SocialLoginStore {
	// 제공자 로그인 화면에서 머무를 수 있는 시간 (BE-031)
	static final Duration AUTHORIZATION_TTL = Duration.ofMinutes(10);
	// 약관·회원 정보를 입력할 시간 (BE-033)
	static final Duration PENDING_SIGNUP_TTL = Duration.ofMinutes(15);
	// 연결 표를 받고 제공자 화면으로 넘어가기까지의 시간 (BE-032)
	static final Duration LINK_TICKET_TTL = Duration.ofMinutes(2);

	private final StringRedisTemplate redisTemplate;
	private final JsonConverter jsonConverter;

	static String authorizationKey(String requestId) {
		return "oauth2:" + requestId + ":authorizationRequest";
	}

	static String pendingSignupKey(String pendingId) {
		return "oauth2:" + pendingId + ":pendingSignup";
	}

	static String linkTicketKey(String ticket) {
		return "oauth2:" + ticket + ":linkTicket";
	}

	@Override
	public void saveAuthorization(String requestId, StoredAuthorization authorization) {
		save(authorizationKey(requestId), authorization, AUTHORIZATION_TTL);
	}

	@Override
	public Optional<StoredAuthorization> takeAuthorization(String requestId) {
		return take(authorizationKey(requestId), StoredAuthorization.class);
	}

	@Override
	public void savePendingSignup(String pendingId, PendingSignup pendingSignup) {
		save(pendingSignupKey(pendingId), pendingSignup, PENDING_SIGNUP_TTL);
	}

	@Override
	public Optional<PendingSignup> findPendingSignup(String pendingId) {
		return Optional.ofNullable(redisTemplate.opsForValue().get(pendingSignupKey(pendingId)))
			.map(json -> jsonConverter.fromJson(json, PendingSignup.class));
	}

	@Override
	public void deletePendingSignup(String pendingId) {
		redisTemplate.delete(pendingSignupKey(pendingId));
	}

	@Override
	public void saveLinkTicket(String ticket, LinkTicket linkTicket) {
		save(linkTicketKey(ticket), linkTicket, LINK_TICKET_TTL);
	}

	@Override
	public Optional<LinkTicket> takeLinkTicket(String ticket) {
		return take(linkTicketKey(ticket), LinkTicket.class);
	}

	private void save(String key, Object value, Duration ttl) {
		redisTemplate.opsForValue().set(key, jsonConverter.toJson(value), ttl);
	}

	// GETDEL 한 명령으로 꺼내고 지운다. 같은 키로 동시에 두 요청이 와도 하나만 값을 받는다
	private <T> Optional<T> take(String key, Class<T> type) {
		return Optional.ofNullable(redisTemplate.opsForValue().getAndDelete(key))
			.map(json -> jsonConverter.fromJson(json, type));
	}
}
