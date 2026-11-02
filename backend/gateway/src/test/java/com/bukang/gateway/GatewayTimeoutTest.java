package com.bukang.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.support.RouteMetadataUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.bukang.jwt.JwtProvider;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

// 서비스가 늦거나 꺼져 있을 때 게이트웨이가 504·503을 RsData JSON으로 응답하는지 확인한다
// 10초를 기다리지 않도록 응답 시간 제한을 500ms로 줄인다. 주문 서비스는 늦게 답하고, 캐시 서비스는 꺼져 있다
@SpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
	properties = "spring.cloud.gateway.server.webflux.httpclient.response-timeout=500ms"
)
@ActiveProfiles("test")
class GatewayTimeoutTest {

	private static final List<String> OTHER_SERVICES = List.of("member", "payout", "file", "product", "queue");
	// 응답 시간 제한(500ms)보다 충분히 길게 잡는다
	private static final long SLOW_RESPONSE_MILLIS = 3000;
	private static HttpServer slowStub;
	private static HttpServer okStub;

	private final HttpClient client = HttpClient.newHttpClient();

	@Value("${local.server.port}")
	private int gatewayPort;

	@Autowired
	private JwtProvider jwtProvider;

	@Autowired
	private RouteLocator routeLocator;

	@DynamicPropertySource
	static void serviceUris(DynamicPropertyRegistry registry) throws IOException {
		slowStub = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		// 늦게 답하는 동안 다른 요청이 막히지 않도록 요청마다 스레드를 쓴다
		slowStub.setExecutor(Executors.newCachedThreadPool());
		slowStub.createContext("/", exchange -> {
			try {
				Thread.sleep(SLOW_RESPONSE_MILLIS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			respondOk(exchange);
		});
		slowStub.start();
		registry.add("gateway.services.order", () -> "http://localhost:" + slowStub.getAddress().getPort());

		// 꺼진 서비스: 포트를 잠깐 열었다 닫아, 아무도 듣지 않는 포트로 보낸다
		int closedPort;
		try (ServerSocket socket = new ServerSocket(0)) {
			closedPort = socket.getLocalPort();
		}
		registry.add("gateway.services.cash", () -> "http://localhost:" + closedPort);

		okStub = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		okStub.createContext("/", GatewayTimeoutTest::respondOk);
		okStub.start();
		for (String service : OTHER_SERVICES) {
			registry.add("gateway.services." + service, () -> "http://localhost:" + okStub.getAddress().getPort());
		}
	}

	@AfterAll
	static void stopStubs() {
		slowStub.stop(0);
		okStub.stop(0);
	}

	@Test
	@DisplayName("서비스 응답이 제한 시간보다 늦으면 504와 GATEWAY_TIMEOUT 코드를 JSON으로 응답한다")
	void respondsGatewayTimeoutWhenServiceIsSlow() throws Exception {
		HttpResponse<String> response = get("/api/v1/order/orders");

		assertThat(response.statusCode()).isEqualTo(504);
		assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
			contentType -> assertThat(contentType).startsWith("application/json"));
		assertThat(response.body()).isEqualTo("{\"status\":504,\"code\":\"GATEWAY_TIMEOUT\","
			+ "\"message\":\"서버 응답이 늦어요. 잠시 뒤 다시 시도해 주세요.\",\"data\":null}");
	}

	@Test
	@DisplayName("서비스에 연결할 수 없으면 503과 SERVICE_UNAVAILABLE 코드를 JSON으로 응답한다")
	void respondsServiceUnavailableWhenServiceIsDown() throws Exception {
		HttpResponse<String> response = get("/api/v1/cash/wallets");

		assertThat(response.statusCode()).isEqualTo(503);
		assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
			contentType -> assertThat(contentType).startsWith("application/json"));
		assertThat(response.body()).isEqualTo("{\"status\":503,\"code\":\"SERVICE_UNAVAILABLE\","
			+ "\"message\":\"서버에 연결할 수 없어요. 잠시 뒤 다시 시도해 주세요.\",\"data\":null}");
	}

	@Test
	@DisplayName("정산 배치 수동 실행 라우트만 5분으로 늘어나 있고, 나머지 정산 API는 기본 제한을 쓴다")
	void payoutDevBatchRouteHasLongerResponseTimeout() {
		Route devBatch = routeLocator.getRoutes()
			.filter(route -> route.getId().equals("payout-dev-batch"))
			.blockFirst();
		Route payout = routeLocator.getRoutes()
			.filter(route -> route.getId().equals("payout-service"))
			.blockFirst();

		assertThat(devBatch).isNotNull();
		// NettyRoutingFilter처럼 숫자나 문자열 모두 ms 단위 숫자로 읽는다
		Object devBatchTimeout = devBatch.getMetadata().get(RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR);
		assertThat(Long.parseLong(String.valueOf(devBatchTimeout))).isEqualTo(300000L);
		assertThat(payout).isNotNull();
		assertThat(payout.getMetadata()).doesNotContainKey(RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR);
	}

	@Test
	@DisplayName("예치금 충전 확정 라우트는 Toss 승인 대기보다 긴 30초로 늘어나 있다")
	void chargeConfirmRouteHasLongerResponseTimeout() {
		Route chargeConfirm = routeLocator.getRoutes()
			.filter(route -> route.getId().equals("cash-charge-confirm"))
			.blockFirst();

		assertThat(chargeConfirm).isNotNull();
		Object timeout = chargeConfirm.getMetadata().get(RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR);
		assertThat(Long.parseLong(String.valueOf(timeout))).isEqualTo(30000L);
	}

	private static void respondOk(HttpExchange exchange) throws IOException {
		byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
		exchange.sendResponseHeaders(200, body.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(body);
		}
	}

	private HttpResponse<String> get(String path) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + gatewayPort + path))
			.header("Authorization", "Bearer " + jwtProvider.issueAccessToken(1L))
			.GET()
			.build();
		return client.send(request, HttpResponse.BodyHandlers.ofString());
	}

}
