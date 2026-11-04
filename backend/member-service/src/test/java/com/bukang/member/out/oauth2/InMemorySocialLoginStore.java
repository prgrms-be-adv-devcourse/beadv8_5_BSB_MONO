package com.bukang.member.out.oauth2;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 테스트용 소셜 로그인 상태 저장소. 테스트에서는 Redis를 띄우지 않으므로 메모리에 둔다 (만료 시간은 확인하지 않는다)
 * 꺼내면 지우는 동작(take)은 Redis 구현과 같다. @Import(InMemorySocialLoginStore.Config.class)로 바꿔 끼운다
 */
public class InMemorySocialLoginStore implements SocialLoginStore {
	private final Map<String, StoredAuthorization> authorizations = new ConcurrentHashMap<>();
	private final Map<String, PendingSignup> pendingSignups = new ConcurrentHashMap<>();
	private final Map<String, LinkTicket> linkTickets = new ConcurrentHashMap<>();

	@Override
	public void saveAuthorization(String requestId, StoredAuthorization authorization) {
		authorizations.put(requestId, authorization);
	}

	@Override
	public Optional<StoredAuthorization> takeAuthorization(String requestId) {
		return Optional.ofNullable(authorizations.remove(requestId));
	}

	@Override
	public void savePendingSignup(String pendingId, PendingSignup pendingSignup) {
		pendingSignups.put(pendingId, pendingSignup);
	}

	@Override
	public Optional<PendingSignup> findPendingSignup(String pendingId) {
		return Optional.ofNullable(pendingSignups.get(pendingId));
	}

	@Override
	public void deletePendingSignup(String pendingId) {
		pendingSignups.remove(pendingId);
	}

	@Override
	public void saveLinkTicket(String ticket, LinkTicket linkTicket) {
		linkTickets.put(ticket, linkTicket);
	}

	@Override
	public Optional<LinkTicket> takeLinkTicket(String ticket) {
		return Optional.ofNullable(linkTickets.remove(ticket));
	}

	public void clear() {
		authorizations.clear();
		pendingSignups.clear();
		linkTickets.clear();
	}

	@TestConfiguration
	public static class Config {
		@Bean
		@Primary
		public InMemorySocialLoginStore inMemorySocialLoginStore() {
			return new InMemorySocialLoginStore();
		}
	}
}
