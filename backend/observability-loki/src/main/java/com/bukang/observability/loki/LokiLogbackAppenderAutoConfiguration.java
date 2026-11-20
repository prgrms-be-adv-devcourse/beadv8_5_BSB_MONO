package com.bukang.observability.loki;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;

/**
 * 운영(prod)에서 Logback 로그를 Loki로도 보낸다 (CRU-95).
 * Boot가 만든 Logback 설정은 그대로 두고, loki-appender.xml의 appender를 루트 로거에 더한다.
 * prod 프로필이면서 환경변수 LOKI_URL이 있을 때만 붙어, 로컬·테스트·CI에서는 아무 일도 하지 않는다.
 */
@AutoConfiguration
@Profile("prod")
@ConditionalOnProperty("LOKI_URL")
public class LokiLogbackAppenderAutoConfiguration {

	@Bean
	InitializingBean lokiLogbackAppenderInstaller(@Value("${spring.application.name}") String serviceName) {
		return () -> {
			LoggerContext context = (LoggerContext)LoggerFactory.getILoggerFactory();
			context.putProperty("LOKI_SERVICE", serviceName);
			JoranConfigurator configurator = new JoranConfigurator();
			configurator.setContext(context);
			// doConfigure는 기존 appender를 지우지 않고 더한다 (context.reset()을 부르지 않으므로)
			configurator.doConfigure(getClass().getResource("/loki-appender.xml"));
		};
	}
}
