package com.bukang.observability;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;

/**
 * bootRun(dev)에서 Logback 로그를 OTLP로도 내보낸다.
 * Spring Boot는 로그를 내보내는 부품만 만들고 Logback과 잇지 않아서, appender를 루트 로거에 직접 붙인다.
 * 로그 주소가 있을 때만 붙인다. 콘솔 appender는 건드리지 않아 터미널 출력은 그대로다.
 */
@AutoConfiguration
@ConditionalOnProperty("management.opentelemetry.logging.export.otlp.endpoint")
public class OtelLogbackAppenderAutoConfiguration {

	@Bean
	InitializingBean otelLogbackAppenderInstaller(OpenTelemetry openTelemetry) {
		return () -> {
			LoggerContext context = (LoggerContext)LoggerFactory.getILoggerFactory();
			OpenTelemetryAppender appender = new OpenTelemetryAppender();
			appender.setContext(context);
			appender.setName("OTEL");
			// Boot가 만든 OpenTelemetry(로그 내보내기 포함)를 쓴다. 문서의 install()과 같은 일을 이 appender에만 한다
			appender.setOpenTelemetry(openTelemetry);
			appender.start();
			context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(appender);
		};
	}
}
