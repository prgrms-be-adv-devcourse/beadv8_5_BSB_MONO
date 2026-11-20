package com.bukang.cash.out.bank;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.bukang.common.global.web.InternalKeyHeader;

// 은행 송금은 모든 프로파일에서 HTTP로 부른다. 지금 은행은 같은 앱 안의 가짜 은행(fakebank)이다
@Configuration
public class BankClientConfig {
	@Bean
	public BankTransferClient bankTransferClient(
		RestClient.Builder builder,
		@Value("${cash.clients.bank.base-url}") String baseUrl,
		@Value("${cash.clients.bank.timeout}") Duration timeout,
		@Value("${internal.api-key}") String internalKey
	) {
		HttpClient httpClient = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_1_1)
			.connectTimeout(timeout)
			.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(timeout);
		return new HttpBankTransferClient(builder
			.baseUrl(baseUrl)
			.requestFactory(requestFactory)
			// 가짜 은행도 내부 인증 키 검사를 받는다 (InternalKeyFilterConfig)
			.defaultHeader(InternalKeyHeader.NAME, internalKey)
			.build());
	}
}
