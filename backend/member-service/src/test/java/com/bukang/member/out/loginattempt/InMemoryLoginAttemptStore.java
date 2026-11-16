package com.bukang.member.out.loginattempt;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 테스트용 로그인 시도 저장소. 테스트에서는 Redis를 띄우지 않으므로 메모리에 둔다 (만료 시간은 확인하지 않는다)
 * 이 클래스의 Config는 컴포넌트 스캔에 잡혀, 로그인을 부르는 모든 스프링 테스트에서 Redis 구현을 대신한다
 * 시간이 지나면 풀리는지는 RedisLoginAttemptStoreTest가 진짜 Redis로 확인한다
 */
public class InMemoryLoginAttemptStore implements LoginAttemptStore {
	private final Map<String, Long> counts = new ConcurrentHashMap<>();

	@Override
	public long record(String emailHash, int maxFailures, Duration blockTime) {
		return counts.merge(emailHash, 1L, Long::sum);
	}

	@Override
	public void clear(String emailHash) {
		counts.remove(emailHash);
	}

	public Map<String, Long> counts() {
		return counts;
	}

	public void clearAll() {
		counts.clear();
	}

	@Configuration
	public static class Config {
		@Bean
		@Primary
		public InMemoryLoginAttemptStore inMemoryLoginAttemptStore() {
			return new InMemoryLoginAttemptStore();
		}
	}
}
