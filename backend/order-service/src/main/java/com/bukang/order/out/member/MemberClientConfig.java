package com.bukang.order.out.member;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.bukang.order.out.InternalHttpClients;

/**
 * 회원 서비스를 실제 HTTP로 부를 때만 켠다 (order.clients.member.mode=http, BUK-26)
 * 기본값(fake)이면 FakeMemberAdminAdapter가 대신 답해, 회원 서비스를 띄우지 않고도 관리자 API를 개발할 수 있다
 */
@Configuration
@ConditionalOnProperty(prefix = "order.clients.member", name = "mode", havingValue = "http")
public class MemberClientConfig {
	@Bean
	public HttpMemberAdminAdapter httpMemberAdminAdapter(
		@Value("${order.clients.member.base-url}") String baseUrl,
		@Value("${order.clients.timeout}") Duration timeout
	) {
		return new HttpMemberAdminAdapter(InternalHttpClients.create(baseUrl, timeout));
	}
}
