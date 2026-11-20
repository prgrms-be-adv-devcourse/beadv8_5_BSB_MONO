package com.bukang.cash.out.pg;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Toss페이먼츠를 실제 HTTP로 부를 때만 켠다 (cash.clients.pg.mode=http, PRO-81)
 * 기본값(fake)이면 FakePaymentClient가 대신 답해, Toss 서버 없이도 충전을 개발할 수 있다
 */
@Configuration
@ConditionalOnProperty(prefix = "cash.clients.pg", name = "mode", havingValue = "http")
public class PaymentClientConfig {
	@Bean
	public TossPaymentClient tossPaymentClient(
		RestClient.Builder builder,
		@Value("${cash.clients.pg.base-url}") String baseUrl,
		@Value("${cash.clients.pg.secret-key}") String secretKey,
		@Value("${cash.clients.pg.confirm-timeout}") Duration confirmTimeout,
		@Value("${cash.clients.pg.lookup-timeout}") Duration lookupTimeout
	) {
		// 승인용·조회용이 시간 제한만 다르므로, 한 빌더를 복사해 설정이 서로 섞이지 않게 한다
		return new TossPaymentClient(
			restClient(builder.clone(), baseUrl, confirmTimeout),
			restClient(builder.clone(), baseUrl, lookupTimeout),
			secretKey);
	}

	// 연결과 응답에 각각 timeout을 걸어, 한 번 부를 때 최악 timeout x 2만큼 걸린다
	private RestClient restClient(RestClient.Builder builder, String baseUrl, Duration timeout) {
		HttpClient httpClient = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_1_1)
			.connectTimeout(timeout)
			.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(timeout);

		return builder
			.baseUrl(baseUrl)
			.requestFactory(requestFactory)
			.build();
	}
}
