package com.bukang.inquiry.out.member;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.bukang.inquiry.out.InternalHttpClients;

/**
 * 회원 서비스를 실제 HTTP로 부를 때만 켠다 (inquiry.clients.member.mode=http)
 * fake이면 Fake*Adapter가 대신 답해, 회원 서비스를 띄우지 않고도 개발할 수 있다
 */
@Configuration
@ConditionalOnProperty(prefix = "inquiry.clients.member", name = "mode", havingValue = "http")
public class MemberClientConfig {
	@Bean
	public HttpMemberAdminAdapter httpMemberAdminAdapter(
		@Value("${inquiry.clients.member.base-url}") String baseUrl,
		@Value("${inquiry.clients.timeout}") Duration timeout
	) {
		return new HttpMemberAdminAdapter(InternalHttpClients.create(baseUrl, timeout));
	}

	@Bean
	public HttpMemberSellerAdapter httpMemberSellerAdapter(
		@Value("${inquiry.clients.member.base-url}") String baseUrl,
		@Value("${inquiry.clients.timeout}") Duration timeout
	) {
		return new HttpMemberSellerAdapter(InternalHttpClients.create(baseUrl, timeout));
	}

	@Bean
	public HttpMemberNicknamesAdapter httpMemberNicknamesAdapter(
		@Value("${inquiry.clients.member.base-url}") String baseUrl,
		@Value("${inquiry.clients.timeout}") Duration timeout
	) {
		return new HttpMemberNicknamesAdapter(InternalHttpClients.create(baseUrl, timeout));
	}
}
