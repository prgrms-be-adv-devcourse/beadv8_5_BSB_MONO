package com.bukang.inquiry.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

// 테스트 설정에는 회원·상품·주문 서비스 mode가 없으므로, 세 서비스를 테스트가 정하는 가짜로 대신한다
@TestConfiguration
public class StubPortsConfig {
	@Bean
	public StubMemberAdminPort stubMemberAdminPort() {
		return new StubMemberAdminPort();
	}

	@Bean
	public StubMemberSellerPort stubMemberSellerPort() {
		return new StubMemberSellerPort();
	}

	@Bean
	public StubMemberNicknamesPort stubMemberNicknamesPort() {
		return new StubMemberNicknamesPort();
	}

	@Bean
	public StubRaceSellerPort stubRaceSellerPort() {
		return new StubRaceSellerPort();
	}

	@Bean
	public StubOrderLookupPort stubOrderLookupPort() {
		return new StubOrderLookupPort();
	}
}
