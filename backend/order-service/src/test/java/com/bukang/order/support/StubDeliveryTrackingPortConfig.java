package com.bukang.order.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

// 가짜 어댑터(FakeDeliveryTrackingAdapter) 대신 테스트가 배송 조회 결과를 정한다 (BUK-55)
@TestConfiguration
public class StubDeliveryTrackingPortConfig {
	@Bean
	@Primary
	public StubDeliveryTrackingPort stubDeliveryTrackingPort() {
		return new StubDeliveryTrackingPort();
	}
}
