package com.bukang.order.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

// 가짜 어댑터(FakeKitOptionAdapter, 늘 옵션 없음) 대신 테스트가 정하는 키트 옵션을 쓴다
@TestConfiguration
public class StubKitOptionPortConfig {
	@Bean
	@Primary
	public StubKitOptionPort stubKitOptionPort() {
		return new StubKitOptionPort();
	}
}
