package com.bukang.gateway.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.bukang.jwt.JwtProperties;
import com.bukang.jwt.JwtProvider;
import com.sun.net.httpserver.HttpServer;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

// 모든 서비스 주소를 가짜 서버 하나로 돌려 두고, 게이트웨이가 토큰을 검사해 회원 ID를 넘기는지 확인한다
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GatewayAuthenticationTest {

	private static final List<String> SERVICES =
		List.of("member", "cash", "payout", "file", "order", "product", "queue", "notification", "board");
	private static final String MEMBER_ID_HEADER = "X-Member-Id";
	private static final String LOGIN_REQUIRED = "AUTH_LOGIN_REQUIRED";
	private static final String TOKEN_EXPIRED = "AUTH_TOKEN_EXPIRED";
	private static final String INVALID_TOKEN = "AUTH_INVALID_TOKEN";
	// 가짜 서버가 받은 X-Member-Id 헤더 값 (없으면 "없음")
	private static final List<String> RECEIVED_MEMBER_IDS = new CopyOnWriteArrayList<>();
	private static HttpServer stub;

	private final HttpClient client = HttpClient.newHttpClient();

	@Value("${local.server.port}")
	private int gatewayPort;

	@Value("${jwt.secret}")
	private String secret;

	@Autowired
	private JwtProvider jwtProvider;

	@DynamicPropertySource
	static void serviceUris(DynamicPropertyRegistry registry) throws IOException {
		stub = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		stub.createContext("/", exchange -> {
			String memberId = exchange.getRequestHeaders().getFirst(MEMBER_ID_HEADER);
			RECEIVED_MEMBER_IDS.add(memberId == null ? "없음" : memberId);
			byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, body.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(body);
			}
		});
		stub.start();
		for (String service : SERVICES) {
			registry.add("gateway.services." + service, () -> "http://localhost:" + stub.getAddress().getPort());
		}
	}

	@AfterAll
	static void stopStub() {
		stub.stop(0);
	}

	@BeforeEach
	void clearReceived() {
		RECEIVED_MEMBER_IDS.clear();
	}

	@Test
	@DisplayName("유효한 토큰이면 토큰 속 회원 ID를 X-Member-Id 헤더로 서비스에 넘긴다")
	void forwardsMemberIdForValidToken() throws Exception {
		HttpResponse<String> response = send(request("/api/v1/order/orders")
			.header("Authorization", "Bearer " + jwtProvider.issueAccessToken(42L)));

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(RECEIVED_MEMBER_IDS).containsExactly("42");
	}

	@Test
	@DisplayName("클라이언트가 X-Member-Id를 직접 넣어도 토큰 속 회원 ID로 바꿔 넘긴다")
	void replacesSpoofedMemberIdWithTokenMemberId() throws Exception {
		HttpResponse<String> response = send(request("/api/v1/order/orders")
			.header("Authorization", "Bearer " + jwtProvider.issueAccessToken(42L))
			.header(MEMBER_ID_HEADER, "999"));

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(RECEIVED_MEMBER_IDS).containsExactly("42");
	}

	@Test
	@DisplayName("토큰이 없으면 서비스로 넘기지 않고 401을 RsData 형식으로 응답한다")
	void rejectsRequestWithoutToken() throws Exception {
		HttpResponse<String> response = send(request("/api/v1/order/orders"));

		assertUnauthorized(response, LOGIN_REQUIRED, "로그인이 필요합니다.");
	}

	@Test
	@DisplayName("토큰 없이 X-Member-Id만 넣어 보내도 401로 응답한다")
	void rejectsSpoofedMemberIdWithoutToken() throws Exception {
		HttpResponse<String> response = send(request("/api/v1/order/orders").header(MEMBER_ID_HEADER, "999"));

		assertUnauthorized(response, LOGIN_REQUIRED, "로그인이 필요합니다.");
	}

	@Test
	@DisplayName("JWT 형식이 아닌 토큰이면 401과 AUTH_INVALID_TOKEN으로 응답한다")
	void rejectsMalformedToken() throws Exception {
		HttpResponse<String> response = send(request("/api/v1/order/orders")
			.header("Authorization", "Bearer not-a-jwt"));

		assertUnauthorized(response, INVALID_TOKEN, "유효하지 않은 토큰입니다.");
	}

	@ParameterizedTest(name = "Authorization: {0}")
	@DisplayName("Bearer 형식이 아니거나 토큰이 비어 있으면 토큰이 없는 것으로 보고 AUTH_LOGIN_REQUIRED로 응답한다")
	@ValueSource(strings = {"Basic abc", "Bearer "})
	void rejectsNonBearerAuthorizationAsMissingToken(String authorization) throws Exception {
		HttpResponse<String> response = send(request("/api/v1/order/orders").header("Authorization", authorization));

		assertUnauthorized(response, LOGIN_REQUIRED, "로그인이 필요합니다.");
	}

	@Test
	@DisplayName("다른 키로 서명한 토큰이면 401로 응답한다")
	void rejectsTokenSignedWithOtherKey() throws Exception {
		JwtProvider otherProvider = new JwtProvider(
			new JwtProperties("other-secret-key-0000-1111-2222-3333", Duration.ofMinutes(30), Duration.ofDays(14)),
			Clock.systemUTC());

		HttpResponse<String> response = send(request("/api/v1/order/orders")
			.header("Authorization", "Bearer " + otherProvider.issueAccessToken(42L)));

		assertUnauthorized(response, INVALID_TOKEN, "유효하지 않은 토큰입니다.");
	}

	@Test
	@DisplayName("만료된 토큰이면 재발급할 수 있도록 만료 메시지로 401을 응답한다")
	void rejectsExpiredToken() throws Exception {
		Clock pastClock = Clock.fixed(Instant.now().minus(Duration.ofHours(1)), ZoneOffset.UTC);
		JwtProvider pastProvider = new JwtProvider(
			new JwtProperties(secret, Duration.ofMinutes(30), Duration.ofDays(14)), pastClock);

		HttpResponse<String> response = send(request("/api/v1/order/orders")
			.header("Authorization", "Bearer " + pastProvider.issueAccessToken(42L)));

		assertUnauthorized(response, TOKEN_EXPIRED, "만료된 토큰입니다.");
	}

	@Test
	@DisplayName("Refresh Token을 Access Token 자리에 쓰면 401로 응답한다")
	void rejectsRefreshToken() throws Exception {
		HttpResponse<String> response = send(request("/api/v1/order/orders")
			.header("Authorization", "Bearer " + refreshToken(42L)));

		assertUnauthorized(response, INVALID_TOKEN, "유효하지 않은 토큰입니다.");
	}

	@Test
	@DisplayName("서명 없는 토큰(alg none)이면 401로 응답한다")
	void rejectsUnsignedToken() throws Exception {
		Instant now = Instant.now();
		String unsigned = Jwts.builder()
			.subject("42")
			.claim("type", "ACCESS")
			.issuedAt(Date.from(now))
			.expiration(Date.from(now.plus(Duration.ofMinutes(30))))
			.compact();

		HttpResponse<String> response = send(request("/api/v1/order/orders")
			.header("Authorization", "Bearer " + unsigned));

		assertUnauthorized(response, INVALID_TOKEN, "유효하지 않은 토큰입니다.");
	}

	@Test
	@DisplayName("CORS 사전 요청(OPTIONS)은 토큰 없이 통과하되, 클라이언트가 보낸 X-Member-Id는 지운다")
	void passesPreflightWithoutTokenAndRemovesSpoofedMemberId() throws Exception {
		HttpResponse<String> response = send(request("/api/v1/order/orders")
			.header(MEMBER_ID_HEADER, "999")
			.method("OPTIONS", HttpRequest.BodyPublishers.noBody()));

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(RECEIVED_MEMBER_IDS).containsExactly("없음");
	}

	@ParameterizedTest(name = "{0} {1}")
	@DisplayName("소셜 로그인 시작·콜백·가입과 가입 본인인증은 토큰 없이 통과하고, 클라이언트가 보낸 X-Member-Id는 지운다")
	@CsvSource({
		"GET, /api/v1/auth/oauth2/authorize/kakao",
		"GET, /api/v1/auth/oauth2/callback/google?code=abc&state=xyz",
		"GET, /api/v1/auth/oauth2/signup",
		"POST, /api/v1/auth/oauth2/join",
		"POST, /api/v1/auth/identity-verifications",
		"POST, /api/v1/auth/identity-verifications/abc/complete"
	})
	void passesSocialLoginAndIdentityVerificationWithoutToken(String method, String path) throws Exception {
		HttpResponse<String> response = send(request(path)
			.header(MEMBER_ID_HEADER, "999")
			.method(method, HttpRequest.BodyPublishers.noBody()));

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(RECEIVED_MEMBER_IDS).containsExactly("없음");
	}

	@Test
	@DisplayName("토스 가상계좌 입금 알림(POST)은 토큰 없이 통과하고, 클라이언트가 보낸 X-Member-Id는 지운다")
	void passesTossDepositWebhookWithoutToken() throws Exception {
		HttpResponse<String> response = send(request("/api/v1/cash/webhooks/toss/deposit")
			.header(MEMBER_ID_HEADER, "999")
			.POST(HttpRequest.BodyPublishers.noBody()));

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(RECEIVED_MEMBER_IDS).containsExactly("없음");
	}

	@Test
	@DisplayName("토스 입금 알림 주소라도 POST가 아니면 토큰이 필요하다")
	void requiresTokenForTossDepositWebhookNonPost() throws Exception {
		HttpResponse<String> response = send(request("/api/v1/cash/webhooks/toss/deposit"));

		assertUnauthorized(response, LOGIN_REQUIRED, "로그인이 필요합니다.");
	}

	@Test
	@DisplayName("가입 본인인증 주소라도 POST가 아니면 토큰이 필요하다")
	void requiresTokenForIdentityVerificationNonPost() throws Exception {
		HttpResponse<String> response = send(request("/api/v1/auth/identity-verifications/abc"));

		assertUnauthorized(response, LOGIN_REQUIRED, "로그인이 필요합니다.");
	}

	@ParameterizedTest(name = "{0}")
	@DisplayName("회원가입·로그인·토큰 재발급·로그아웃은 토큰 없이 통과하고, 클라이언트가 보낸 X-Member-Id는 지운다")
	@ValueSource(strings = {"/api/v1/auth/join", "/api/v1/auth/login", "/api/v1/auth/reissue", "/api/v1/auth/logout"})
	void passesPublicPathWithoutToken(String path) throws Exception {
		HttpResponse<String> response = send(request(path)
			.header(MEMBER_ID_HEADER, "999")
			.POST(HttpRequest.BodyPublishers.noBody()));

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(RECEIVED_MEMBER_IDS).containsExactly("없음");
	}

	@Test
	@DisplayName("이메일 중복 확인 GET은 로그인 없이 통과하고, 클라이언트가 보낸 X-Member-Id는 지운다")
	void passesEmailAvailabilityWithoutToken() throws Exception {
		HttpResponse<String> response = send(request("/api/v1/member/members/email-availability?email=a@b.com")
			.header(MEMBER_ID_HEADER, "999"));

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(RECEIVED_MEMBER_IDS).containsExactly("없음");
	}

	@Test
	@DisplayName("이메일 중복 확인 주소라도 GET이 아니면 토큰이 필요하다")
	void requiresTokenForEmailAvailabilityNonGet() throws Exception {
		HttpResponse<String> response = send(request("/api/v1/member/members/email-availability")
			.POST(HttpRequest.BodyPublishers.noBody()));

		assertUnauthorized(response, "AUTH_LOGIN_REQUIRED", "로그인이 필요합니다.");
	}

	@Test
	@DisplayName("광고 수신 거부 POST는 메시지 링크로 로그인 없이 부르므로 통과하고, 클라이언트가 보낸 X-Member-Id는 지운다")
	void passesMarketingUnsubscribeWithoutToken() throws Exception {
		HttpResponse<String> response = send(request("/api/v1/member/marketing-consent/unsubscribe")
			.header(MEMBER_ID_HEADER, "999")
			.POST(HttpRequest.BodyPublishers.ofString("{\"token\": \"t\"}")));

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(RECEIVED_MEMBER_IDS).containsExactly("없음");
	}

	@Test
	@DisplayName("광고 수신 거부 주소라도 POST가 아니면 토큰이 필요하다")
	void requiresTokenForMarketingUnsubscribeNonPost() throws Exception {
		HttpResponse<String> response = send(request("/api/v1/member/marketing-consent/unsubscribe"));

		assertUnauthorized(response, "AUTH_LOGIN_REQUIRED", "로그인이 필요합니다.");
	}

	@Test
	@DisplayName("공개 주소와 앞부분만 같은 주소는 토큰이 필요하다")
	void requiresTokenForPathOnlyStartingWithPublicPath() throws Exception {
		HttpResponse<String> response = send(request("/api/v1/auth/login-history")
			.POST(HttpRequest.BodyPublishers.noBody()));

		assertUnauthorized(response, LOGIN_REQUIRED, "로그인이 필요합니다.");
	}

	@Test
	@DisplayName("상품 서비스 GET 요청은 로그인 없이 통과하고, 클라이언트가 보낸 X-Member-Id는 지운다")
	void passesProductReadWithoutToken() throws Exception {
		HttpResponse<String> response = send(request("/api/v1/product/races/1").header(MEMBER_ID_HEADER, "999"));

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(RECEIVED_MEMBER_IDS).containsExactly("없음");
	}

	@Test
	@DisplayName("상품 서비스 GET 요청에 유효한 토큰이 있으면 회원 ID를 넘긴다 (이미 신청함 표시 등에 쓴다)")
	void forwardsMemberIdForProductReadWithToken() throws Exception {
		HttpResponse<String> response = send(request("/api/v1/product/races/1")
			.header("Authorization", "Bearer " + jwtProvider.issueAccessToken(42L)));

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(RECEIVED_MEMBER_IDS).containsExactly("42");
	}

	@Test
	@DisplayName("상품 서비스 GET 요청에 만료된 토큰이 있으면 로그인하지 않은 요청으로 넘긴다")
	void passesProductReadWithExpiredTokenAsAnonymous() throws Exception {
		Clock pastClock = Clock.fixed(Instant.now().minus(Duration.ofHours(1)), ZoneOffset.UTC);
		JwtProvider pastProvider = new JwtProvider(
			new JwtProperties(secret, Duration.ofMinutes(30), Duration.ofDays(14)), pastClock);

		HttpResponse<String> response = send(request("/api/v1/product/races")
			.header("Authorization", "Bearer " + pastProvider.issueAccessToken(42L)));

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(RECEIVED_MEMBER_IDS).containsExactly("없음");
	}

	@Test
	@DisplayName("상품 서비스라도 GET이 아닌 요청(대회 등록 등)은 토큰이 필요하다")
	void requiresTokenForProductWrite() throws Exception {
		HttpResponse<String> response = send(request("/api/v1/product/races")
			.POST(HttpRequest.BodyPublishers.noBody()));

		assertUnauthorized(response, LOGIN_REQUIRED, "로그인이 필요합니다.");
	}

	@Test
	@DisplayName("게시판 GET은 로그인 없이 통과하고, 클라이언트가 보낸 X-Member-Id는 지운다")
	void passesBoardReadWithoutToken() throws Exception {
		HttpResponse<String> response = send(request("/api/v1/board/posts/1").header(MEMBER_ID_HEADER, "999"));

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(RECEIVED_MEMBER_IDS).containsExactly("없음");
	}

	@Test
	@DisplayName("게시판 GET에 유효한 토큰이 있으면 회원 ID를 넘긴다 (내가 누른 좋아요 표시 등에 쓴다)")
	void forwardsMemberIdForBoardReadWithToken() throws Exception {
		HttpResponse<String> response = send(request("/api/v1/board/posts/1")
			.header("Authorization", "Bearer " + jwtProvider.issueAccessToken(42L)));

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(RECEIVED_MEMBER_IDS).containsExactly("42");
	}

	@ParameterizedTest(name = "{0}")
	@DisplayName("게시판 쓰기(글·댓글 POST, 좋아요 PUT, 지우기 DELETE)는 토큰이 필요하다")
	@ValueSource(strings = {"POST /api/v1/board/posts", "PUT /api/v1/board/posts/1/like",
		"DELETE /api/v1/board/posts/1", "POST /api/v1/board/posts/1/comments"})
	void requiresTokenForBoardWrite(String methodAndPath) throws Exception {
		String[] parts = methodAndPath.split(" ");
		HttpResponse<String> response = send(request(parts[1])
			.method(parts[0], HttpRequest.BodyPublishers.noBody()));

		assertUnauthorized(response, LOGIN_REQUIRED, "로그인이 필요합니다.");
	}

	@ParameterizedTest(name = "{0}")
	@DisplayName("파일 조회 GET(대회 사진 등)은 로그인 없이 통과한다")
	@ValueSource(strings = {"/api/v1/file/files/1", "/api/v1/file/files?refType=Race&refId=1"})
	void passesFileReadWithoutToken(String path) throws Exception {
		HttpResponse<String> response = send(request(path));

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(RECEIVED_MEMBER_IDS).containsExactly("없음");
	}

	@Test
	@DisplayName("파일 업로드 등 GET이 아닌 파일 요청은 토큰이 필요하다")
	void requiresTokenForFileWrite() throws Exception {
		HttpResponse<String> response = send(request("/api/v1/file/files")
			.POST(HttpRequest.BodyPublishers.noBody()));

		assertUnauthorized(response, LOGIN_REQUIRED, "로그인이 필요합니다.");
	}

	@ParameterizedTest(name = "{0}")
	@DisplayName("경로에 ..·. 칸, 인코딩된 .·/·\\·%, ;가 있으면 토큰 검사 전에 400으로 거절하고 서비스로 넘기지 않는다")
	@ValueSource(strings = {
		"/api/v1/file/files/../../../../internal/v1/file/refs/RACE/1/files",
		"/api/v1/file/files/%2e%2e/%2e%2e/%2e%2e/%2e%2e/internal/v1/file/refs/RACE/1/files",
		"/api/v1/file/files/%2E%2E/%2E%2E/%2E%2E/%2E%2E/internal/v1/file/refs/RACE/1/files",
		"/api/v1/file/files/.%2e/.%2e/.%2e/.%2e/internal/v1/file/refs/RACE/1/files",
		"/api/v1/file/files/..%2f..%2f..%2f..%2finternal/v1/file/refs/RACE/1/files",
		"/api/v1/file/files/..%2F..%2F..%2F..%2Finternal/v1/file/refs/RACE/1/files",
		"/api/v1/file/files/./x",
		"/api/v1/file/files/..;/..;/..;/..;/internal/v1/file/refs/RACE/1/files",
		"/api/v1/file/files/x;y",
		"/api/v1/file/files/..%5c..%5c..%5c..%5cinternal/v1/file/refs/RACE/1/files",
		"/api/v1/file/files/..%5C..%5C..%5C..%5Cinternal/v1/file/refs/RACE/1/files",
		"/api/v1/file/files/%252e%252e/%252e%252e/%252e%252e/%252e%252e/internal/v1/file/refs/RACE/1/files",
		"/api/v1/file/files/%252E%252E/x"
	})
	void rejectsTraversalPathWithoutToken(String path) throws Exception {
		HttpRequest.Builder builder = request(path);
		// HttpClient가 경로를 정리하거나 다시 인코딩하지 않고 그대로 보내는지 먼저 확인한다
		assertThat(builder.build().uri().getRawPath()).isEqualTo(path);

		HttpResponse<String> response = send(builder);

		assertBadRequestPath(response);
	}

	@Test
	@DisplayName("유효한 토큰이 있어도 경로에 .. 칸이 있으면 400으로 거절한다")
	void rejectsTraversalPathWithValidToken() throws Exception {
		String path = "/api/v1/order/orders/../../member/members";
		HttpRequest.Builder builder = request(path)
			.header("Authorization", "Bearer " + jwtProvider.issueAccessToken(42L));
		assertThat(builder.build().uri().getRawPath()).isEqualTo(path);

		HttpResponse<String> response = send(builder);

		assertBadRequestPath(response);
	}

	@Test
	@DisplayName("CORS 사전 요청(OPTIONS)도 경로에 .. 칸이 있으면 400으로 거절한다")
	void rejectsTraversalPathForPreflight() throws Exception {
		String path = "/api/v1/file/files/../../../../internal/v1/file/refs/RACE/1/files";
		HttpResponse<String> response = send(request(path)
			.method("OPTIONS", HttpRequest.BodyPublishers.noBody()));

		assertBadRequestPath(response);
	}

	// HttpClient(URI)는 역슬래시를 보낼 수 없으므로 소켓으로 요청 줄을 직접 써서 보낸다
	// 지금은 Netty가 필터보다 먼저 빈 본문 400으로 막으므로 본문은 보지 않고, 서비스로 넘어가지 않는지만 본다
	@Test
	@DisplayName("경로에 역슬래시가 있으면 400으로 거절한다")
	void rejectsBackslashPath() throws Exception {
		String path = "/api/v1/file/files/..\\..\\..\\..\\internal/v1/file/refs/RACE/1/files";
		String response;
		try (Socket socket = new Socket("localhost", gatewayPort)) {
			OutputStream out = socket.getOutputStream();
			out.write(("GET " + path + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n")
				.getBytes(StandardCharsets.UTF_8));
			out.flush();
			response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		}

		assertThat(response).startsWith("HTTP/1.1 400");
		assertThat(RECEIVED_MEMBER_IDS).isEmpty();
	}

	// 게이트웨이는 Refresh Token을 발급하지 않으므로, 발급 규칙(jti·familyId 등)에 기대지 않고 type만 REFRESH인 토큰을 직접 서명한다
	private String refreshToken(long memberId) {
		Instant now = Instant.now();
		return Jwts.builder()
			.subject(String.valueOf(memberId))
			.claim("type", "REFRESH")
			.issuedAt(Date.from(now))
			.expiration(Date.from(now.plus(Duration.ofDays(1))))
			.signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
			.compact();
	}

	private void assertUnauthorized(HttpResponse<String> response, String code, String message) {
		assertThat(response.statusCode()).isEqualTo(401);
		assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
			contentType -> assertThat(contentType).startsWith("application/json"));
		assertThat(response.body())
			.isEqualTo("{\"status\":401,\"code\":\"" + code + "\",\"message\":\"" + message + "\",\"data\":null}");
		assertThat(RECEIVED_MEMBER_IDS).isEmpty();
	}

	private void assertBadRequestPath(HttpResponse<String> response) {
		assertThat(response.statusCode()).isEqualTo(400);
		assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
			contentType -> assertThat(contentType).startsWith("application/json"));
		assertThat(response.body())
			.isEqualTo("{\"status\":400,\"code\":\"AUTH_INVALID_PATH\",\"message\":\"잘못된 요청 경로입니다.\",\"data\":null}");
		assertThat(RECEIVED_MEMBER_IDS).isEmpty();
	}

	private HttpRequest.Builder request(String path) {
		return HttpRequest.newBuilder(URI.create("http://localhost:" + gatewayPort + path));
	}

	private HttpResponse<String> send(HttpRequest.Builder builder) throws IOException, InterruptedException {
		return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
	}

}
