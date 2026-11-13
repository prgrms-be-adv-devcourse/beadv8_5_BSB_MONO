package com.bukang.cash.out.bank;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

// 은행 송금은 모든 프로파일에서 HTTP로 부른다. 지금 은행은 같은 앱 안의 가짜 은행(fakebank)이다
@Configuration
public class BankClientConfig {
	@Bean
	public BankTransferClient bankTransferClient(
		@Value("${cash.clients.bank.base-url}") String baseUrl,
		@Value("${cash.clients.bank.timeout}") Duration timeout
	) {
		HttpClient httpClient = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_1_1)
			.connectTimeout(timeout)
			.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(timeout);
		return new HttpBankTransferClient(RestClient.builder()
			.baseUrl(baseUrl)
			.requestFactory(requestFactory)
			.build());
	}
}
