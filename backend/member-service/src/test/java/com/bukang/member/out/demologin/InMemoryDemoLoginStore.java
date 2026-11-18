package com.bukang.member.out.demologin;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 테스트용 데모 로그인 횟수 저장소. Redis를 띄우지 않으므로 메모리에 두고 만료 시간은 확인하지 않는다
 * @Import(InMemoryDemoLoginStore.Config.class)로 바꿔 끼운다
 */
public class InMemoryDemoLoginStore implements DemoLoginStore {
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
		public InMemoryDemoLoginStore inMemoryDemoLoginStore() {
			return new InMemoryDemoLoginStore();
		}
	}
}
