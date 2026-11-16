package com.bukang.member.out;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 다른 서비스의 내부 API(/internal/v1/...)를 부를 RestClient를 만든다 (order-service의 InternalHttpClients와 같은 기준)
 * 상대가 느려도 요청이 오래 붙잡히지 않게 연결·응답 모두 시간 제한을 둔다. 서비스끼리는 평문 HTTP/1.1로 부른다
 */
public final class InternalHttpClients {
	private InternalHttpClients() {
	}

	public static RestClient create(String baseUrl, Duration timeout) {
		return RestClient.builder()
			.baseUrl(baseUrl)
			.requestFactory(requestFactory(timeout))
			.build();
	}

	// 소셜 로그인처럼 라이브러리가 쓸 클라이언트에 시간 제한만 끼울 때도 같은 요청 팩토리를 쓴다
	public static JdkClientHttpRequestFactory requestFactory(Duration timeout) {
		HttpClient httpClient = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_1_1)
			.connectTimeout(timeout)
			.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(timeout);
		return requestFactory;
	}
}
