package com.bukang.order.out.race;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import com.bukang.order.out.InternalHttpClients;
import com.bukang.order.out.kit.HttpKitOptionAdapter;
import com.bukang.order.out.seat.HttpCrewSeatHoldAdapter;
import com.bukang.order.out.seat.HttpSeatHoldAdapter;

/**
 * 상품 서비스(B)를 실제 HTTP로 부를 때만 켠다 (order.clients.product.mode=http, PRO-165)
 * 종목 정보·자리 확보·크루 자리 확보·키트 옵션은 같은 서비스라 한 설정으로 함께 바꾼다
 * 기본값(fake)이면 FakeRaceCourseAdapter·FakeSeatHoldAdapter가 대신 답해, 상품 서비스를 띄우지 않고도 주문을 개발할 수 있다
 */
@Configuration
@ConditionalOnProperty(prefix = "order.clients.product", name = "mode", havingValue = "http")
public class ProductClientConfig {
	// RestClient는 빈으로 두지 않는다. 다른 서비스용 RestClient가 생겨도 타입으로 주입할 때 겹치지 않게 한다
	private final RestClient restClient;

	public ProductClientConfig(
		@Value("${order.clients.product.base-url}") String baseUrl,
		@Value("${order.clients.timeout}") Duration timeout
	) {
		this.restClient = InternalHttpClients.create(baseUrl, timeout);
	}

	@Bean
	public HttpRaceCourseAdapter httpRaceCourseAdapter() {
		return new HttpRaceCourseAdapter(restClient);
	}

	@Bean
	public HttpKitOptionAdapter httpKitOptionAdapter() {
		return new HttpKitOptionAdapter(restClient);
	}

	@Bean
	public HttpSeatHoldAdapter httpSeatHoldAdapter() {
		return new HttpSeatHoldAdapter(restClient);
	}

	@Bean
	public HttpCrewSeatHoldAdapter httpCrewSeatHoldAdapter() {
		return new HttpCrewSeatHoldAdapter(restClient);
	}
}
