package com.bukang.order.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

// 가짜 어댑터(FakeCrewAdapter, 누구나 크루원) 대신 테스트가 크루원 여부를 정한다
@TestConfiguration
public class StubCrewPortConfig {
	@Bean
	@Primary
	public StubCrewPort stubCrewPort() {
		return new StubCrewPort();
	}
}
