package com.bukang.ai.out.file;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.bukang.ai.out.member.MemberClientConfig;

// 기록증 원본 받기 (BUK-275). 보기 주소는 다른 내부 API처럼 짧게, 사진 내려받기는 크기를 생각해 따로 길게 기다린다
@Configuration
public class FileClientConfig {
	@Bean
	public CertImagePort certImagePort(
		@Value("${ai.clients.file.base-url}") String baseUrl,
		@Value("${ai.clients.timeout}") Duration timeout,
		@Value("${ai.clients.file.download-timeout}") Duration downloadTimeout
	) {
		// 내려받기는 presigned 절대 주소로 부르므로 baseUrl은 쓰이지 않는다
		return new HttpCertImageAdapter(MemberClientConfig.restClient(baseUrl, timeout),
			MemberClientConfig.restClient(baseUrl, downloadTimeout));
	}
}
