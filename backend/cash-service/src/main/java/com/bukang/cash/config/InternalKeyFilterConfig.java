package com.bukang.cash.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 다른 서비스가 부르는 내부 API와, 정산금 송금이 HTTP로 부르는 가짜 은행에 내부 인증 키 검사를 건다
@Configuration
public class InternalKeyFilterConfig {
	@Bean
	public FilterRegistrationBean<InternalKeyFilter> internalKeyFilter(@Value("${internal.api-key}") String key) {
		FilterRegistrationBean<InternalKeyFilter> registration =
			new FilterRegistrationBean<>(new InternalKeyFilter(key));
		registration.addUrlPatterns("/internal/*", "/fakebank/*");
		return registration;
	}
}
