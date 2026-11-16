package com.bukang.inquiry.out.order;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.bukang.inquiry.out.InternalHttpClients;

/**
 * 주문 서비스를 실제 HTTP로 부를 때만 켠다 (inquiry.clients.order.mode=http). fake이면 FakeOrderLookupAdapter가 답한다
 */
@Configuration
@ConditionalOnProperty(prefix = "inquiry.clients.order", name = "mode", havingValue = "http")
public class OrderClientConfig {
	@Bean
	public HttpOrderLookupAdapter httpOrderLookupAdapter(
		@Value("${inquiry.clients.order.base-url}") String baseUrl,
		@Value("${inquiry.clients.timeout}") Duration timeout
	) {
		return new HttpOrderLookupAdapter(InternalHttpClients.create(baseUrl, timeout));
	}
}
