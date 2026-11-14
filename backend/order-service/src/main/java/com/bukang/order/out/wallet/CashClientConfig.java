package com.bukang.order.out.wallet;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.bukang.order.out.InternalHttpClients;

/**
 * 예치금 서비스(D)를 실제 HTTP로 부를 때만 켠다 (order.clients.cash.mode=http, PRO-158)
 * 기본값(fake)이면 FakeWalletAdapter가 대신 답해, 예치금 서비스를 띄우지 않고도 주문을 개발할 수 있다
 */
@Configuration
@ConditionalOnProperty(prefix = "order.clients.cash", name = "mode", havingValue = "http")
public class CashClientConfig {
	@Bean
	public HttpWalletAdapter httpWalletAdapter(
		@Value("${order.clients.cash.base-url}") String baseUrl,
		@Value("${order.clients.timeout}") Duration timeout,
		// C안 시제품 추천값(미확정): 예치금 결제 동시 호출 4개, 자리 기다림 1초
		@Value("${order.clients.cash.max-concurrent-pays:4}") int maxConcurrentPays,
		@Value("${order.clients.cash.pay-permit-wait:1s}") Duration payPermitWait
	) {
		return new HttpWalletAdapter(InternalHttpClients.create(baseUrl, timeout), maxConcurrentPays, payPermitWait);
	}
}
