package com.bukang.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.bukang.jwt.JwtProvider;
import com.sun.net.httpserver.HttpServer;

// 서비스마다 가짜 서버를 하나씩 띄우고, 게이트웨이가 요청을 어느 서버로 어떤 주소로 넘기는지 확인한다
// 토큰 검사는 GatewayAuthenticationTest에서 확인하고, 여기서는 유효한 토큰을 붙여 라우팅만 본다
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GatewayRoutingTest {

	private static final List<String> SERVICES =
		List.of("member", "cash", "payout", "file", "order", "product", "queue", "notification");
	private static final List<HttpServer> STUBS = new CopyOnWriteArrayList<>();
	// 가짜 서버가 받은 요청을 "서비스이름 경로" 형식으로 남긴다
	private static final List<String> RECEIVED = new CopyOnWriteArrayList<>();

	private final HttpClient client = HttpClient.newHttpClient();

	@Value("${local.server.port}")
	private int gatewayPort;

	@Autowired
	private JwtProvider jwtProvider;

	@DynamicPropertySource
	static void serviceUris(DynamicPropertyRegistry registry) throws IOException {
		for (String service : SERVICES) {
			HttpServer stub = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
			stub.createContext("/", exchange -> {
				RECEIVED.add(service + " " + exchange.getRequestURI());
				byte[] body = service.getBytes(StandardCharsets.UTF_8);
				exchange.sendResponseHeaders(200, body.length);
				try (OutputStream out = exchange.getResponseBody()) {
					out.write(body);
				}
			});
			stub.start();
			STUBS.add(stub);
			registry.add("gateway.services." + service, () -> "http://localhost:" + stub.getAddress().getPort());
		}
	}

	@AfterAll
	static void stopStubs() {
		STUBS.forEach(stub -> stub.stop(0));
	}

	@BeforeEach
	void clearReceived() {
		RECEIVED.clear();
	}

	@ParameterizedTest(name = "{0} → {1}")
	@DisplayName("경로에 맞는 서비스로 주소를 자르지 않고 그대로 넘긴다")
	@CsvSource({
		"/api/v1/member/members/1, member",
		"/api/v1/auth/login, member",
		"/api/v1/cash/wallets, cash",
		"/api/v1/cash/charges/confirm, cash",
		"/api/v1/payout/payouts, payout",
		"/api/v1/payout/dev/batches/pay, payout",
		"/api/v1/file/files/1, file",
		"/api/v1/order/orders?page=0, order",
		"/api/v1/product/races/1, product",
		"/api/v1/queue/1, queue",
		"/api/v1/notification/notifications, notification"
	})
	void routesToServiceWithoutStrippingPath(String path, String service) throws Exception {
		HttpResponse<String> response = get(path);

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(RECEIVED).containsExactly(service + " " + path);
	}

	@Test
	@DisplayName("내부 전용 주소(/internal/**)는 서비스로 넘기지 않고 404로 응답한다 (RsData 형식)")
	void doesNotRouteInternalPath() throws Exception {
		HttpResponse<String> response = get("/internal/v1/file/refs/RACE/1/files");

		assertNotFound(response);
	}

	@Test
	@DisplayName("라우팅 규칙에 없는 주소는 404를 RsData 형식으로 응답한다")
	void respondsNotFoundForUnknownPath() throws Exception {
		HttpResponse<String> response = get("/api/v1/unknown/1");

		assertNotFound(response);
	}

	@Test
	@DisplayName("서비스 응답에 HSTS 헤더를 붙여 브라우저가 HTTPS로만 접속하게 한다")
	void addsHstsHeaderToServiceResponse() throws Exception {
		HttpResponse<String> response = get("/api/v1/order/orders");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.headers().firstValue("Strict-Transport-Security")).hasValue("max-age=31536000");
	}

	private void assertNotFound(HttpResponse<String> response) {
		assertThat(response.statusCode()).isEqualTo(404);
		assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
			contentType -> assertThat(contentType).startsWith("application/json"));
		assertThat(response.body()).isEqualTo("{\"status\":404,\"code\":\"NOT_FOUND\","
			+ "\"message\":\"요청한 주소를 찾을 수 없어요.\",\"data\":null}");
		assertThat(RECEIVED).isEmpty();
	}

	private HttpResponse<String> get(String path) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + gatewayPort + path))
			.header("Authorization", "Bearer " + jwtProvider.issueAccessToken(1L))
			.GET()
			.build();
		return client.send(request, HttpResponse.BodyHandlers.ofString());
	}

}
