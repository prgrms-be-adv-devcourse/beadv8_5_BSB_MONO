package com.bukang.product.out.file;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 파일 서비스를 실제 HTTP로 부를 때만 켠다 (product.clients.file.mode=http, CRU-141)
 * fake이면 FakeRaceThumbnailAdapter가 대신 답한다
 * 목록 응답이 파일 서비스 때문에 늦어지지 않게 연결·응답 모두 시간 제한을 둔다. 서비스끼리는 평문 HTTP/1.1로 부른다
 */
@Configuration
@ConditionalOnProperty(prefix = "product.clients.file", name = "mode", havingValue = "http")
public class FileClientConfig {
	@Bean
	public HttpRaceThumbnailAdapter httpRaceThumbnailAdapter(
		@Value("${product.clients.file.base-url}") String baseUrl,
		@Value("${product.clients.timeout}") Duration timeout
	) {
		HttpClient httpClient = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_1_1)
			.connectTimeout(timeout)
			.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(timeout);
		return new HttpRaceThumbnailAdapter(RestClient.builder()
			.baseUrl(baseUrl)
			.requestFactory(requestFactory)
			.build());
	}
}
