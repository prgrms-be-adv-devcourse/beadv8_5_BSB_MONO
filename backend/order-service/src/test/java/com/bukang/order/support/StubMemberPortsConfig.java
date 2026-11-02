package com.bukang.order.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

// 가짜 어댑터(FakeMemberAdminAdapter·FakeMemberNicknameAdapter) 대신 테스트가 관리자와 닉네임을 정한다
@TestConfiguration
public class StubMemberPortsConfig {
	@Bean
	@Primary
	public StubMemberPorts stubMemberPorts() {
		return new StubMemberPorts();
	}
}
