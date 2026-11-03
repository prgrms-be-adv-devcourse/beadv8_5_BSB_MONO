package com.bukang.order.out.crew;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.bukang.order.out.InternalHttpClients;

/**
 * 크루 확인을 회원 서비스에 실제 HTTP로 물을 때만 켠다 (order.clients.crew.mode=http, BUK-35)
 * 주소는 관리자 확인과 같은 회원 서비스(order.clients.member.base-url)를 쓴다
 */
@Configuration
@ConditionalOnProperty(prefix = "order.clients.crew", name = "mode", havingValue = "http")
public class CrewClientConfig {
	@Bean
	public HttpCrewAdapter httpCrewAdapter(
		@Value("${order.clients.member.base-url}") String baseUrl,
		@Value("${order.clients.timeout}") Duration timeout
	) {
		return new HttpCrewAdapter(InternalHttpClients.create(baseUrl, timeout));
	}
}
