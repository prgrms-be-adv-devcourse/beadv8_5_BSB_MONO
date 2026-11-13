package com.bukang.member.out.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

import com.bukang.member.domain.SocialProvider;
import com.bukang.member.domain.exception.SocialLoginFailedException;
import com.bukang.member.out.oauth2.SocialOAuth2Client.AuthorizationStart;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

// 카카오·구글을 흉내 내는 로컬 서버로 코드 교환을 돌린다. 응답이 늦으면 약 2초 안에 로그인 실패로 끝나야 하고,
// 오류 응답과 정상 응답은 타임아웃을 달기 전과 같게 처리돼야 한다
class SpringSocialOAuth2ClientTimeoutTest {
	private static final String CALLBACK = "https://crewrun.example/api/v1/auth/oauth2/callback/";
	private static final String GOOGLE_CLIENT_ID = "google-client";
	private static final long SLOW_MILLIS = 8_000;
	// 제한 시간 2초에 여유를 더한 값. 늦은 응답을 끝까지 기다렸다면 SLOW_MILLIS 이상 걸린다
	private static final Duration LIMIT = Duration.ofSeconds(6);
	private static final String TOKEN_JSON = "{\"access_token\":\"at\",\"token_type\":\"bearer\",\"expires_in\":60}";
	private static final String KAKAO_USER = "{\"id\":123,\"kakao_account\":{\"email\":\"a@b.c\","
		+ "\"is_email_valid\":true,\"is_email_verified\":true}}";
	private static final String GOOGLE_USER = "{\"sub\":\"g-1\",\"email\":\"a@b.c\",\"email_verified\":true,"
		+ "\"name\":\"길동\"}";

	private HttpServer server;
	private ExecutorService executor;
	private RSAKey signingKey;
	// 구글 ID 토큰은 시작 때 만든 nonce를 담아야 해서, 토큰 응답을 만드는 시점에 읽는다
	private volatile String currentNonce;

	@BeforeEach
	void startServer() throws Exception {
		executor = Executors.newCachedThreadPool();
		server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.setExecutor(executor);
		server.start();
		signingKey = new RSAKeyGenerator(2048).keyID("k1").generate();
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
		executor.shutdownNow();
	}

	@Test
	@DisplayName("카카오 토큰 교환 응답이 늦으면 약 2초 안에 소셜 로그인 실패로 끝난다")
	void kakaoTokenExchangeTimesOut() {
		serve("/token", slow(200, TOKEN_JSON));

		assertFailsFast(SocialProvider.KAKAO);
	}

	@Test
	@DisplayName("카카오 사용자 정보 응답이 늦으면 약 2초 안에 소셜 로그인 실패로 끝난다")
	void kakaoUserInfoTimesOut() {
		serve("/token", json(200, TOKEN_JSON));
		serve("/userinfo", slow(200, KAKAO_USER));

		assertFailsFast(SocialProvider.KAKAO);
	}

	@Test
	@DisplayName("카카오가 400 invalid_grant로 답하면 소셜 로그인 실패로 처리한다")
	void kakaoErrorResponseIsLoginFailure() {
		serve("/token", json(400, "{\"error\":\"invalid_grant\",\"error_description\":\"expired\"}"));

		assertFailsFast(SocialProvider.KAKAO);
	}

	@Test
	@DisplayName("카카오 정상 응답이면 회원번호와 이메일을 돌려준다")
	void kakaoSuccess() {
		serve("/token", json(200, TOKEN_JSON));
		serve("/userinfo", json(200, KAKAO_USER));

		SocialIdentity identity = complete(SocialProvider.KAKAO);

		assertThat(identity.providerUserId()).isEqualTo("123");
		assertThat(identity.verifiedEmail()).isEqualTo("a@b.c");
	}

	@Test
	@DisplayName("구글 ID 토큰 공개키(JWK) 응답이 늦으면 약 2초 안에 소셜 로그인 실패로 끝난다")
	void googleJwkFetchTimesOut() {
		serve("/jwks", slow(200, new JWKSet(signingKey.toPublicJWK()).toString()));
		serve("/token", googleToken(IdTokenFlaw.NONE));

		assertFailsFast(SocialProvider.GOOGLE);
	}

	@Test
	@DisplayName("구글 사용자 정보 응답이 늦으면 약 2초 안에 소셜 로그인 실패로 끝난다")
	void googleUserInfoTimesOut() {
		serve("/jwks", json(200, new JWKSet(signingKey.toPublicJWK()).toString()));
		serve("/token", googleToken(IdTokenFlaw.NONE));
		serve("/userinfo", slow(200, GOOGLE_USER));

		assertFailsFast(SocialProvider.GOOGLE);
	}

	@Test
	@DisplayName("구글 정상 응답이면 회원번호와 이메일을 돌려준다")
	void googleSuccess() {
		serveGoogle(IdTokenFlaw.NONE);

		SocialIdentity identity = complete(SocialProvider.GOOGLE);

		assertThat(identity.providerUserId()).isEqualTo("g-1");
		assertThat(identity.verifiedEmail()).isEqualTo("a@b.c");
	}

	@Test
	@DisplayName("구글 ID 토큰의 대상(audience)이 우리 앱이 아니면 소셜 로그인 실패로 처리한다")
	void googleRejectsWrongAudience() {
		serveGoogle(IdTokenFlaw.WRONG_AUDIENCE);

		assertFailsFast(SocialProvider.GOOGLE);
	}

	@Test
	@DisplayName("구글 ID 토큰의 nonce가 시작 때와 다르면 소셜 로그인 실패로 처리한다")
	void googleRejectsWrongNonce() {
		serveGoogle(IdTokenFlaw.WRONG_NONCE);

		assertFailsFast(SocialProvider.GOOGLE);
	}

	@Test
	@DisplayName("구글 ID 토큰이 만료됐으면 소셜 로그인 실패로 처리한다")
	void googleRejectsExpiredToken() {
		serveGoogle(IdTokenFlaw.EXPIRED);

		assertFailsFast(SocialProvider.GOOGLE);
	}

	@Test
	@DisplayName("구글 ID 토큰 헤더의 typ이 JWT가 아니면 소셜 로그인 실패로 처리한다")
	void googleRejectsWrongTokenType() {
		serveGoogle(IdTokenFlaw.WRONG_TYPE);

		assertFailsFast(SocialProvider.GOOGLE);
	}

	@Test
	@DisplayName("구글 ID 토큰이 다른 키로 서명됐으면 소셜 로그인 실패로 처리한다")
	void googleRejectsForgedSignature() throws Exception {
		RSAKey otherKey = new RSAKeyGenerator(2048).keyID("k1").generate();
		serve("/jwks", json(200, new JWKSet(otherKey.toPublicJWK()).toString()));
		serve("/token", googleToken(IdTokenFlaw.NONE));
		serve("/userinfo", json(200, GOOGLE_USER));

		assertFailsFast(SocialProvider.GOOGLE);
	}

	private void serveGoogle(IdTokenFlaw flaw) {
		serve("/jwks", json(200, new JWKSet(signingKey.toPublicJWK()).toString()));
		serve("/token", googleToken(flaw));
		serve("/userinfo", json(200, GOOGLE_USER));
	}

	private void assertFailsFast(SocialProvider provider) {
		Instant started = Instant.now();

		assertThatThrownBy(() -> complete(provider)).isInstanceOf(SocialLoginFailedException.class);
		assertThat(Duration.between(started, Instant.now())).isLessThan(LIMIT);
	}

	private SocialIdentity complete(SocialProvider provider) {
		String base = "http://localhost:" + server.getAddress().getPort();
		SocialOAuth2Client client = new SpringSocialOAuth2Client(List.of(kakao(base), google(base)));
		AuthorizationStart start = client.begin(provider, new MockHttpServletRequest());
		currentNonce = start.authorization().attributes().get("nonce");
		return client.complete(start.authorization(), "code", start.authorization().state());
	}

	private HttpHandler googleToken(IdTokenFlaw flaw) {
		return exchange -> {
			String body = "{\"access_token\":\"at\",\"token_type\":\"bearer\",\"expires_in\":60,"
				+ "\"scope\":\"openid profile email\",\"id_token\":\"" + idToken(flaw) + "\"}";
			json(200, body).handle(exchange);
		};
	}

	private String idToken(IdTokenFlaw flaw) {
		String base = "http://localhost:" + server.getAddress().getPort();
		Instant now = Instant.now();
		JWTClaimsSet claims = new JWTClaimsSet.Builder()
			.issuer(base)
			.subject("g-1")
			.audience(flaw == IdTokenFlaw.WRONG_AUDIENCE ? "other-app" : GOOGLE_CLIENT_ID)
			.issueTime(Date.from(now.minusSeconds(10)))
			.expirationTime(Date.from(flaw == IdTokenFlaw.EXPIRED ? now.minusSeconds(3600) : now.plusSeconds(600)))
			.claim("nonce", nonceHash(flaw == IdTokenFlaw.WRONG_NONCE ? "other-nonce" : currentNonce))
			.claim("email", "a@b.c")
			.claim("email_verified", true)
			.build();
		try {
			SignedJWT jwt = new SignedJWT(header(flaw), claims);
			jwt.sign(new RSASSASigner(signingKey));
			return jwt.serialize();
		} catch (JOSEException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private static JWSHeader header(IdTokenFlaw flaw) {
		JWSHeader.Builder header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("k1");
		if (flaw == IdTokenFlaw.WRONG_TYPE) {
			header.type(new JOSEObjectType("at+jwt"));
		}
		return header.build();
	}

	private static String nonceHash(String nonce) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(nonce.getBytes(StandardCharsets.UTF_8));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private static ClientRegistration kakao(String base) {
		return ClientRegistration.withRegistrationId(SocialProvider.KAKAO.getPathValue())
			.clientId("kakao-client")
			.clientSecret("kakao-secret")
			.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
			.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
			.redirectUri(CALLBACK + "kakao")
			.scope("profile_nickname", "account_email")
			.authorizationUri(base + "/authorize")
			.tokenUri(base + "/token")
			.userInfoUri(base + "/userinfo")
			.userNameAttributeName("id")
			.build();
	}

	private static ClientRegistration google(String base) {
		return ClientRegistration.withRegistrationId(SocialProvider.GOOGLE.getPathValue())
			.clientId(GOOGLE_CLIENT_ID)
			.clientSecret("google-secret")
			.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
			.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
			.redirectUri(CALLBACK + "google")
			.scope("openid", "profile", "email")
			.authorizationUri(base + "/authorize")
			.tokenUri(base + "/token")
			.userInfoUri(base + "/userinfo")
			.jwkSetUri(base + "/jwks")
			.issuerUri(base)
			.userNameAttributeName("sub")
			.build();
	}

	private void serve(String path, HttpHandler handler) {
		server.createContext(path, handler);
	}

	private static HttpHandler json(int status, String body) {
		return exchange -> {
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(status, bytes.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(bytes);
			}
		};
	}

	// 응답 헤더를 보내기 전에 오래 멈춘다
	private static HttpHandler slow(int status, String body) {
		return exchange -> {
			try {
				Thread.sleep(SLOW_MILLIS);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				return;
			}
			try {
				json(status, body).handle(exchange);
			} catch (IOException ignored) {
				// 클라이언트가 이미 연결을 끊었다
			}
		};
	}

	private enum IdTokenFlaw {
		NONE, WRONG_AUDIENCE, WRONG_NONCE, EXPIRED, WRONG_TYPE
	}
}
