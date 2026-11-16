package com.bukang.member.out.payoutaccount;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 테스트용 계좌 확인 횟수 제한. 테스트에서는 Redis를 띄우지 않으므로 메모리에 센다 (하루 기간은 확인하지 않는다)
 * @Import(InMemoryPayoutAccountVerifyLimiter.Config.class)로 바꿔 끼운다
 */
public class InMemoryPayoutAccountVerifyLimiter implements PayoutAccountVerifyLimiter {
	private static final int DAILY_LIMIT = 5;

	private final Map<Long, Integer> counts = new ConcurrentHashMap<>();

	@Override
	public boolean tryAcquire(long memberId) {
		return counts.merge(memberId, 1, Integer::sum) <= DAILY_LIMIT;
	}

	@Override
	public void release(long memberId) {
		counts.merge(memberId, -1, Integer::sum);
	}

	public void clear() {
		counts.clear();
	}

	@TestConfiguration
	public static class Config {
		@Bean
		@Primary
		public InMemoryPayoutAccountVerifyLimiter inMemoryPayoutAccountVerifyLimiter() {
			return new InMemoryPayoutAccountVerifyLimiter();
		}
	}
}
