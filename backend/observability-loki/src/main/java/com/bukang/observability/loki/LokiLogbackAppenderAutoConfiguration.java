package com.bukang.observability.loki;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;

/**
 * 운영(prod)에서 Logback 로그를 Loki로도 보낸다 (CRU-95).
 * Boot가 만든 Logback 설정은 그대로 두고, loki-appender.xml의 appender를 루트 로거에 더한다.
 * prod 프로필이면서 LOKI_URL이 있을 때만 붙어, 로컬·테스트·CI에서는 아무 일도 하지 않는다.
 * shortcut: 빈을 만들 때 붙어서, 그 전의 시작 로그와 빈 생성 전에 난 시작 실패는 Loki로 가지 않는다(kubectl logs로 본다).
 *   시작 실패도 알림이 필요해지면 LoggingApplicationListener 뒤에 도는 ApplicationListener로 옮긴다
 */
@AutoConfiguration
@Profile("prod")
@ConditionalOnProperty("LOKI_URL")
public class LokiLogbackAppenderAutoConfiguration {

	private static final String APPENDER_NAME = "LOKI";

	@Bean
	InitializingBean lokiLogbackAppenderInstaller(Environment environment) {
		return () -> {
			LoggerContext context = (LoggerContext)LoggerFactory.getILoggerFactory();
			if (context.getLogger(Logger.ROOT_LOGGER_NAME).getAppender(APPENDER_NAME) != null) {
				return; // 같은 JVM에서 두 번 붙으면 로그가 두 번 간다
			}
			// 켜짐 조건과 같은 곳(Spring Environment)에서 읽어 XML의 ${...}로 넘긴다
			context.putProperty("LOKI_URL", environment.getRequiredProperty("LOKI_URL"));
			context.putProperty("LOKI_PASSWORD", environment.getProperty("LOKI_PASSWORD", ""));
			context.putProperty("LOKI_SERVICE", environment.getRequiredProperty("spring.application.name"));
			JoranConfigurator configurator = new JoranConfigurator();
			configurator.setContext(context);
			// doConfigure는 기존 appender를 지우지 않고 더한다 (context.reset()을 부르지 않으므로)
			configurator.doConfigure(getClass().getResource("/loki-appender.xml"));
		};
	}
}
