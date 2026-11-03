package com.bukang.member.config.payoutaccount;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.bukang.member.domain.exception.SellerAccountVerifyUnavailableException;
import com.bukang.member.out.InternalHttpClients;
import com.bukang.member.out.payoutaccount.FakePayoutAccountVerifier;
import com.bukang.member.out.payoutaccount.OpenBankingAccountVerifier;
import com.bukang.member.out.payoutaccount.PayoutAccountVerifier;
import com.bukang.member.out.payoutaccount.PayoutAccountVerifyLimiter;
import com.bukang.member.out.payoutaccount.RedisPayoutAccountVerifyLimiter;

import lombok.extern.slf4j.Slf4j;

/**
 * 판매자 정산 계좌 확인 (BE-031 결정 7)
 * 키가 있으면 오픈뱅킹으로 확인한다. 키가 없으면 운영은 확인을 실패로 처리하고(신청 불가), 그 밖은 가짜 확인으로 통과시킨다
 */
@Slf4j
@Configuration
public class PayoutAccountConfig {
	@Bean
	public PayoutAccountVerifier payoutAccountVerifier(PayoutAccountProperties properties, Environment environment,
		Clock clock) {
		return create(properties, environment.acceptsProfiles(Profiles.of("prod")), clock);
	}

	@Bean
	public PayoutAccountVerifyLimiter payoutAccountVerifyLimiter(StringRedisTemplate redisTemplate,
		PayoutAccountProperties properties) {
		return new RedisPayoutAccountVerifyLimiter(redisTemplate, properties.dailyLimit(), properties.window());
	}

	static PayoutAccountVerifier create(PayoutAccountProperties properties, boolean prod, Clock clock) {
		PayoutAccountProperties.OpenBanking openbanking = properties.openbanking();
		if (openbanking.hasKeys()) {
			return new OpenBankingAccountVerifier(
				InternalHttpClients.create(openbanking.baseUrl(), openbanking.timeout()), openbanking, clock);
		}
		if (prod) {
			return inquiry -> {
				log.warn("오픈뱅킹 키가 없어 정산 계좌를 확인하지 못합니다. 판매자 신청을 받지 않습니다.");
				throw new SellerAccountVerifyUnavailableException();
			};
		}
		return new FakePayoutAccountVerifier();
	}
}
