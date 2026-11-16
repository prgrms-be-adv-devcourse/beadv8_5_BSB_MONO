package com.bukang.order.out.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.bukang.common.global.web.InternalKeyHeader;
import com.sun.net.httpserver.HttpServer;

@ExtendWith(OutputCaptureExtension.class)
class CashClientConfigInternalKeyTest {
	// 바쁜 CI 러너에서도 시간 제한에 걸리지 않게 넉넉히 준다. 이 테스트는 헤더가 붙는지만 본다
	private static final Duration TIMEOUT = Duration.ofSeconds(10);

	@Test
	@DisplayName("예치금 서비스 내부 API를 부를 때 내부 인증 키 헤더를 붙인다")
	void sendsInternalKey(CapturedOutput output) throws IOException {
		String key = sendAndCaptureKey("{}", output, baseUrl -> new CashClientConfig()
			.httpWalletAdapter(baseUrl, TIMEOUT, 4, TIMEOUT, "key-1")
			.cancelPayment(1, 7));

		assertThat(key).isEqualTo("key-1");
	}

	// 작은 HTTP 서버를 띄워 클라이언트가 보낸 내부 인증 키 헤더를 받아 둔다
	// localhost 이름 풀이가 환경마다 다를 수 있어 서버와 주소 모두 루프백 IP로 고정한다
	private static String sendAndCaptureKey(String responseBody, CapturedOutput output, Consumer<String> call)
		throws IOException {
		AtomicReference<String> key = new AtomicReference<>();
		InetAddress loopback = InetAddress.getLoopbackAddress();
		HttpServer server = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
		server.createContext("/", exchange -> {
			key.set(exchange.getRequestHeaders().getFirst(InternalKeyHeader.NAME));
			exchange.getRequestBody().readAllBytes();
			byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.start();
		try {
			call.accept("http://" + loopback.getHostAddress() + ":" + server.getAddress().getPort());
		} catch (RuntimeException exception) {
			// 어댑터는 원인을 경고 로그로만 남기고 감추므로, 실패하면 그 로그를 함께 보여 준다
			throw new AssertionError("예치금 서비스 호출이 실패했습니다. 어댑터 로그: " + output.getAll(), exception);
		} finally {
			server.stop(0);
		}
		return key.get();
	}
}
