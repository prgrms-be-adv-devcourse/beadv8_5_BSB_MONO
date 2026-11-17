package com.bukang.member.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.common.global.web.InternalKeyHeader;
import com.sun.net.httpserver.HttpServer;

class InternalClientConfigInternalKeyTest {
	@Test
	@DisplayName("예치금 서비스에 잔액을 물을 때 내부 인증 키 헤더를 붙인다")
	void sendsInternalKeyToCash() throws IOException {
		String key = sendAndCaptureKey("{\"status\":200,\"message\":\"ok\",\"data\":{\"balance\":0}}",
			baseUrl -> new InternalClientConfig().cashBalancePort(baseUrl, Duration.ofSeconds(2), "key-1")
				.findBalance(7));

		assertThat(key).isEqualTo("key-1");
	}

	// 작은 HTTP 서버를 띄워 클라이언트가 보낸 내부 인증 키 헤더를 받아 둔다
	private static String sendAndCaptureKey(String responseBody, Consumer<String> call) throws IOException {
		AtomicReference<String> key = new AtomicReference<>();
		HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext("/", exchange -> {
			key.set(exchange.getRequestHeaders().getFirst(InternalKeyHeader.NAME));
			byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.start();
		try {
			call.accept("http://localhost:" + server.getAddress().getPort());
		} finally {
			server.stop(0);
		}
		return key.get();
	}
}
