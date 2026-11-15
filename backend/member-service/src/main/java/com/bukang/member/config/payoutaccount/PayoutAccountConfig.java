package com.bukang.member.config.payoutaccount;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.bukang.member.out.InternalHttpClients;
import com.bukang.member.out.payoutaccount.FakePayoutAccountVerifier;
import com.bukang.member.out.payoutaccount.OpenBankingAccountVerifier;
import com.bukang.member.out.payoutaccount.PayoutAccountVerifier;
import com.bukang.member.out.payoutaccount.PayoutAccountVerifyLimiter;
import com.bukang.member.out.payoutaccount.RedisPayoutAccountVerifyLimiter;

/**
 * 판매자 정산 계좌 확인 (BUK-50)
 * 키가 있으면 오픈뱅킹으로 확인하고, 키가 없으면 운영을 포함해 어디서든 가짜 확인(목업)을 쓴다
 * 오픈뱅킹 이용 승인이 거절되어 운영에서도 목업으로 확인한다. 목업은 예금주를 신청자 이름으로 돌려주므로 본인 명의 확인이 항상 통과한다
 */
@Configuration
public class PayoutAccountConfig {
	@Bean
	public PayoutAccountVerifier payoutAccountVerifier(PayoutAccountProperties properties, Clock clock) {
		return create(properties, clock);
	}

	@Bean
	public PayoutAccountVerifyLimiter payoutAccountVerifyLimiter(StringRedisTemplate redisTemplate,
		PayoutAccountProperties properties) {
		return new RedisPayoutAccountVerifyLimiter(redisTemplate, properties.dailyLimit(), properties.window());
	}

	static PayoutAccountVerifier create(PayoutAccountProperties properties, Clock clock) {
		PayoutAccountProperties.OpenBanking openbanking = properties.openbanking();
		if (openbanking.hasKeys()) {
			return new OpenBankingAccountVerifier(
				InternalHttpClients.create(openbanking.baseUrl(), openbanking.timeout()), openbanking, clock);
		}
		return new FakePayoutAccountVerifier();
	}
}
