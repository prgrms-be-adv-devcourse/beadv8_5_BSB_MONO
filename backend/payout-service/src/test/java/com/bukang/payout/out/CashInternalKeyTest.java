package com.bukang.payout.out;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.bukang.common.global.web.InternalKeyHeader;
import com.bukang.payout.domain.PayoutStage;
import com.sun.net.httpserver.HttpServer;

// 정산이 cash 내부 API를 부르는 어댑터 둘이 내부 인증 키 헤더를 붙이는지 본다
class CashInternalKeyTest {
	@Test
	@DisplayName("정산금 이동을 부를 때 내부 인증 키 헤더를 붙인다")
	void transferSendsInternalKey() throws IOException {
		String key = sendAndCaptureKey("{}", baseUrl ->
			new CashPayoutTransferAdapter(RestClient.builder(), baseUrl, "key-1")
				.transfer(new PayoutTransfer(31, 7, 47_250L, 2_750L, PayoutStage.최종_정산)));

		assertThat(key).isEqualTo("key-1");
	}

	@Test
	@DisplayName("회수 가상계좌 발급을 부를 때 내부 인증 키 헤더를 붙인다")
	void claimVirtualAccountSendsInternalKey() throws IOException {
		String key = sendAndCaptureKey("{\"data\":{\"bankName\":\"은행\",\"accountNumber\":\"123\"}}",
			baseUrl -> new CashClaimVirtualAccountAdapter(RestClient.builder(), baseUrl, "key-1")
				.issue("RC-1", 400_000, LocalDate.of(2026, 12, 31)));

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
