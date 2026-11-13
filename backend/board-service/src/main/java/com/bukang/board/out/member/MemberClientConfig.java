package com.bukang.board.out.member;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 회원 서비스를 실제 HTTP로 부를 때만 켠다 (board.clients.member.mode=http). fake이면 FakeMemberAdminAdapter가 대신 답한다
 * 상대가 느려도 요청이 오래 붙잡히지 않게 연결·응답 모두 시간 제한을 둔다. 서비스끼리는 평문 HTTP/1.1로 부른다
 */
@Configuration
@ConditionalOnProperty(prefix = "board.clients.member", name = "mode", havingValue = "http")
public class MemberClientConfig {
	@Bean
	public HttpMemberAdminAdapter httpMemberAdminAdapter(
		@Value("${board.clients.member.base-url}") String baseUrl,
		@Value("${board.clients.timeout}") Duration timeout
	) {
		HttpClient httpClient = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_1_1)
			.connectTimeout(timeout)
			.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(timeout);
		return new HttpMemberAdminAdapter(RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build());
	}
}
