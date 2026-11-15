package com.bukang.cash.out.member;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 회원 서비스를 실제 HTTP로 부를 때만 켠다 (cash.clients.member.mode=http)
 * fake면 FakeMemberAdminAdapter가 대신 답해, 회원 서비스를 띄우지 않고도 관리자 API를 개발할 수 있다
 */
@Configuration
@ConditionalOnProperty(prefix = "cash.clients.member", name = "mode", havingValue = "http")
public class MemberClientConfig {
	@Bean
	public HttpMemberAdminAdapter httpMemberAdminAdapter(
		@Value("${cash.clients.member.base-url}") String baseUrl,
		@Value("${cash.clients.member.timeout}") Duration timeout
	) {
		HttpClient httpClient = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_1_1)
			.connectTimeout(timeout)
			.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(timeout);
		return new HttpMemberAdminAdapter(RestClient.builder()
			.baseUrl(baseUrl)
			.requestFactory(requestFactory)
			.build());
	}
}
