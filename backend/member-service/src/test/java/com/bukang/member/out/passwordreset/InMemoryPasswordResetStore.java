package com.bukang.member.out.passwordreset;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 테스트용 비밀번호 재설정 저장소. 테스트에서는 Redis를 띄우지 않으므로 메모리에 둔다 (만료 시간은 확인하지 않는다)
 * 1분 제한(cooldown)은 기간 없이 "한 번 요청하면 막힘"으로, 하루 횟수는 기간 없이 센다
 * @Import(InMemoryPasswordResetStore.Config.class)로 바꿔 끼운다
 */
public class InMemoryPasswordResetStore implements PasswordResetStore {
	private final Map<String, Long> tokens = new ConcurrentHashMap<>();
	private final Map<Long, String> latestTokens = new ConcurrentHashMap<>();
	private final Map<String, Integer> requestCounts = new ConcurrentHashMap<>();
	private final Map<String, Boolean> cooldowns = new ConcurrentHashMap<>();

	@Override
	public boolean tryAcquireRequest(String emailHash, Duration cooldown, int dailyLimit, Duration window) {
		if (cooldowns.putIfAbsent(emailHash, true) != null) {
			return false;
		}
		return requestCounts.merge(emailHash, 1, Integer::sum) <= dailyLimit;
	}

	@Override
	public void saveToken(long memberId, String tokenHash, Duration ttl) {
		tokens.put(tokenHash, memberId);
		String previous = latestTokens.put(memberId, tokenHash);
		if (previous != null && !previous.equals(tokenHash)) {
			tokens.remove(previous);
		}
	}

	@Override
	public boolean isLatestToken(long memberId, String tokenHash) {
		return tokenHash.equals(latestTokens.get(memberId));
	}

	@Override
	public Optional<Long> findMemberId(String tokenHash) {
		return Optional.ofNullable(tokens.get(tokenHash));
	}

	@Override
	public Optional<Long> takeMemberId(String tokenHash) {
		return Optional.ofNullable(tokens.remove(tokenHash));
	}

	@Override
	public void deleteLatestToken(long memberId) {
		latestTokens.remove(memberId);
	}

	// 1분이 지난 것처럼 다시 요청할 수 있게 한다
	public void clearCooldowns() {
		cooldowns.clear();
	}

	public void clear() {
		tokens.clear();
		latestTokens.clear();
		requestCounts.clear();
		cooldowns.clear();
	}

	@TestConfiguration
	public static class Config {
		@Bean
		@Primary
		public InMemoryPasswordResetStore inMemoryPasswordResetStore() {
			return new InMemoryPasswordResetStore();
		}
	}
}
