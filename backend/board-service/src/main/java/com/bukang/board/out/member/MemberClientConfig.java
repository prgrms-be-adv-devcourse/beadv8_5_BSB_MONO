package com.bukang.board.out.member;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 회원 서비스를 부를 HTTP 설정. 주소는 프로파일별 yaml(board.clients.member.base-url)에 있다
 * 상대가 느려도 요청이 오래 붙잡히지 않게 연결·응답 모두 시간 제한을 둔다 (order-service의 InternalHttpClients와 같은 방식)
 */
@Configuration
public class MemberClientConfig {
	@Bean
	public MemberReplicaPort memberReplicaPort(
		@Value("${board.clients.member.base-url}") String baseUrl,
		@Value("${board.clients.timeout}") Duration timeout
	) {
		return new HttpMemberReplicaAdapter(restClient(baseUrl, timeout));
	}

	static RestClient restClient(String baseUrl, Duration timeout) {
		HttpClient httpClient = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_1_1)
			.connectTimeout(timeout)
			.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(timeout);
		return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
	}
}
