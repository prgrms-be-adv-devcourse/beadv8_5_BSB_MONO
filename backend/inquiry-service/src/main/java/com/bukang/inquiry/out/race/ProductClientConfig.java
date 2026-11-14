package com.bukang.inquiry.out.race;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.bukang.inquiry.out.InternalHttpClients;

/**
 * 상품 서비스를 실제 HTTP로 부를 때만 켠다 (inquiry.clients.product.mode=http). fake이면 FakeRaceSellerAdapter가 답한다
 */
@Configuration
@ConditionalOnProperty(prefix = "inquiry.clients.product", name = "mode", havingValue = "http")
public class ProductClientConfig {
	@Bean
	public HttpRaceSellerAdapter httpRaceSellerAdapter(
		@Value("${inquiry.clients.product.base-url}") String baseUrl,
		@Value("${inquiry.clients.timeout}") Duration timeout
	) {
		return new HttpRaceSellerAdapter(InternalHttpClients.create(baseUrl, timeout));
	}
}
