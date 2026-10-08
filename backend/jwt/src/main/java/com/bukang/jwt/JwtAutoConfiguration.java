package com.bukang.jwt;

import java.time.Clock;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * jwt 모듈을 의존하는 서비스에 JwtProvider 빈을 등록한다.
 * 서비스마다 scanBasePackages를 고치지 않도록 컴포넌트 스캔 대신 자동 설정으로 등록한다.
 */
@AutoConfiguration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public JwtProvider jwtProvider(JwtProperties properties) {
		return new JwtProvider(properties, Clock.systemUTC());
	}
}
