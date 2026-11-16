package com.bukang.member.out.emailcheck;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 테스트용 이메일 확인 횟수 저장소. Redis를 띄우지 않으므로 메모리에 두고 만료 시간은 확인하지 않는다
 * @Import(InMemoryEmailCheckStore.Config.class)로 바꿔 끼운다
 */
public class InMemoryEmailCheckStore implements EmailCheckStore {
	private final Map<String, Integer> counts = new ConcurrentHashMap<>();

	@Override
	public boolean tryAcquire(String ipHash, int limit, Duration window) {
		return counts.merge(ipHash, 1, Integer::sum) <= limit;
	}

	public void clear() {
		counts.clear();
	}

	@TestConfiguration
	public static class Config {
		@Bean
		@Primary
		public InMemoryEmailCheckStore inMemoryEmailCheckStore() {
			return new InMemoryEmailCheckStore();
		}
	}
}
