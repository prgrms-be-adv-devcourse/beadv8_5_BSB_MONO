package com.bukang.product.out.member;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 회원 서비스를 실제 HTTP로 부를 때만 켠다 (product.clients.member.mode=http)
 * fake이면 FakeMemberAdminAdapter가 대신 답해, 회원 서비스를 띄우지 않고도 개발할 수 있다
 * 상대가 느려도 요청이 오래 붙잡히지 않게 연결·응답 모두 시간 제한을 둔다. 서비스끼리는 평문 HTTP/1.1로 부른다
 */
@Configuration
@ConditionalOnProperty(prefix = "product.clients.member", name = "mode", havingValue = "http")
public class MemberClientConfig {
	@Bean
	public HttpMemberAdminAdapter httpMemberAdminAdapter(
		@Value("${product.clients.member.base-url}") String baseUrl,
		@Value("${product.clients.timeout}") Duration timeout
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
