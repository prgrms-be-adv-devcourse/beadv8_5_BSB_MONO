package com.bukang.observability.loki;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.sun.net.httpserver.HttpServer;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;

class LokiLogbackAppenderAutoConfigurationTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(LokiLogbackAppenderAutoConfiguration.class))
		.withPropertyValues("spring.application.name=order-service");

	private final BlockingQueue<String[]> pushes = new LinkedBlockingQueue<>();
	private HttpServer fakeLoki;

	@BeforeEach
	void startFakeLoki() throws Exception {
		fakeLoki = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		fakeLoki.createContext("/loki/api/v1/push", exchange -> {
			String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			pushes.add(new String[] {exchange.getRequestHeaders().getFirst("Authorization"), body});
			exchange.sendResponseHeaders(204, -1);
			exchange.close();
		});
		fakeLoki.start();
	}

	@AfterEach
	void cleanUp() {
		Logger root = rootLogger();
		if (root.getAppender("LOKI") != null) {
			root.getAppender("LOKI").stop();
			root.detachAppender("LOKI");
		}
		fakeLoki.stop(0);
	}

	@Test
	@DisplayName("prod이고 LOKI_URL이 있으면 INFO 이상만 라벨을 붙여 보낸다")
	void sendsInfoAndAboveWithLabelsInProd() throws Exception {
		String url = "http://127.0.0.1:" + fakeLoki.getAddress().getPort() + "/loki/api/v1/push";
		System.setProperty("LOKI_URL", url);
		System.setProperty("LOKI_PASSWORD", "test-password");
		try {
			runner.withPropertyValues("spring.profiles.active=prod", "LOKI_URL=" + url).run(context -> {
				assertThat(rootLogger().getAppender("LOKI")).isNotNull();
				// 로거가 DEBUG를 내보내게 열어, 걸러 내는 일을 appender 필터가 하는지 본다
				Logger log = (Logger)LoggerFactory.getLogger("loki-test");
				log.setLevel(Level.DEBUG);
				log.debug("보내면 안 되는 디버그");
				log.info("보낼 정보 로그");
				log.error("보낼 오류 로그", new IllegalStateException("오류 원인"));
				// batch.timeoutMs(5초)보다 먼저 보내도록 appender를 멈춰 남은 로그를 비운다
				rootLogger().getAppender("LOKI").stop();

				String[] push = pushes.poll(10, TimeUnit.SECONDS);
				assertThat(push).isNotNull();
				String expectedAuth = "Basic " + Base64.getEncoder()
					.encodeToString("alloy:test-password".getBytes(StandardCharsets.UTF_8));
				assertThat(push[0]).isEqualTo(expectedAuth);
				assertThat(push[1])
					.contains("\"namespace\":\"crewrun\"", "\"service\":\"order-service\"", "\"env\":\"prod\"",
						"\"level\":\"INFO\"", "보낼 정보 로그")
					// 예외 스택도 메시지에 실린다 (PatternLayout이 %ex를 스스로 붙인다)
					.contains("\"level\":\"ERROR\"", "java.lang.IllegalStateException: 오류 원인")
					.doesNotContain("보내면 안 되는 디버그");
			});
		} finally {
			System.clearProperty("LOKI_URL");
			System.clearProperty("LOKI_PASSWORD");
		}
	}

	@Test
	@DisplayName("prod가 아니면 붙지 않는다")
	void skipsOutsideProd() {
		runner.withPropertyValues("spring.profiles.active=dev", "LOKI_URL=http://127.0.0.1:1/loki/api/v1/push")
			.run(context -> assertThat(rootLogger().getAppender("LOKI")).isNull());
	}

	@Test
	@DisplayName("LOKI_URL이 없으면 붙지 않는다")
	void skipsWithoutLokiUrl() {
		runner.withPropertyValues("spring.profiles.active=prod")
			.run(context -> assertThat(rootLogger().getAppender("LOKI")).isNull());
	}

	private static Logger rootLogger() {
		return ((LoggerContext)LoggerFactory.getILoggerFactory()).getLogger(Logger.ROOT_LOGGER_NAME);
	}
}
