package com.bukang.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class JwtAutoConfigurationTest {
	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(JwtAutoConfiguration.class));

	@Test
	@DisplayName("jwt.secret이 있으면 JwtProvider를 등록하고 유효 기간 기본값을 쓴다")
	void registersProviderWithDefaultExpirations() {
		runner.withPropertyValues("jwt.secret=jwt-test-secret-key-0000-1111-2222-3333")
			.run(context -> {
				assertThat(context).hasSingleBean(JwtProvider.class);
				JwtProperties properties = context.getBean(JwtProperties.class);
				assertThat(properties.accessTokenExpiration()).isEqualTo(Duration.ofMinutes(30));
				assertThat(properties.refreshTokenExpiration()).isEqualTo(Duration.ofDays(14));
			});
	}

	@Test
	@DisplayName("jwt.secret이 없으면 시작에 실패한다")
	void failsToStartWithoutSecret() {
		runner.run(context -> assertThat(context).hasFailed());
	}
}
