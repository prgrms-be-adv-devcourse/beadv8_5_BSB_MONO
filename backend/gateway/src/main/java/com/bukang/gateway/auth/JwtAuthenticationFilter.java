package com.bukang.gateway.auth;

import java.util.List;
import java.time.Duration;
import java.util.Locale;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.bukang.gateway.error.RsDataResponseWriter;
import com.bukang.jwt.InvalidTokenException;
import com.bukang.jwt.JwtClaims;
import com.bukang.jwt.JwtProvider;
import com.bukang.jwt.TokenType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * 라우팅되는 모든 요청의 Access Token을 검사하고, 통과하면 토큰 속 회원 ID를 X-Member-Id 헤더로 서비스에 넘긴다.
 * 서비스는 이 헤더를 로그인한 회원으로 믿으므로, 클라이언트가 보낸 X-Member-Id는 검사 전에 무조건 지운다 (위조 차단).
 * 라우팅 규칙에 맞지 않는 요청(/internal/** 등)은 이 필터를 거치지 않고 404로 응답한다.
 * 토큰 검사 전에, 경로에 ..·. 칸이나 ;·\·%2e·%2f·%5c·%25가 있는 요청은 400으로 거절한다 (경로 조작으로 공개 주소 판단 우회 차단).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {
	public static final String MEMBER_ID_HEADER = "X-Member-Id";

	private static final String BEARER_PREFIX = "Bearer ";
	// 경로에 있으면 거절하는 문자열 (소문자로 바꾼 경로와 비교한다)
	private static final List<String> FORBIDDEN_PATH_PARTS = List.of(";", "\\", "%2e", "%2f", "%5c", "%25");

	// 400·401 응답의 code. 프론트가 오류 원인을 구분하는 값이라 이 한곳에서만 정의한다
	private static final String CODE_INVALID_PATH = "AUTH_INVALID_PATH";
	private static final String CODE_LOGIN_REQUIRED = "AUTH_LOGIN_REQUIRED";
	private static final String CODE_TOKEN_EXPIRED = "AUTH_TOKEN_EXPIRED";
	private static final String CODE_INVALID_TOKEN = "AUTH_INVALID_TOKEN";

	// Redis가 끊기지 않고 느려지기만 해도 모든 요청이 늦어지지 않게 확인에 따로 건 제한. 넘으면 실패와 같게 통과시킨다
	private static final Duration REVOKED_CHECK_TIMEOUT = Duration.ofMillis(200);

	private static String revokedKey(String jti) {
		return "access-tokens:" + jti + ":revoked";
	}

	private final JwtProvider jwtProvider;
	// 토큰 없이 통과하는 주소는 코드가 아니라 application.yaml의 gateway.auth.public-paths에 둔다
	private final PublicPaths publicPaths;
	// 로그아웃한 Access 토큰 표시를 읽는다. 키 형식은 member-service의 RevokedAccessTokenRepository와 같다 (양쪽 테스트가 같은 값을 확인)
	private final ReactiveStringRedisTemplate redis;

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		// 공개 주소 판단은 정리되지 않은 경로로, 서비스는 ..을 정리한 경로로 처리해 둘이 어긋나므로 이런 경로는 아예 거절한다
		// OPTIONS를 포함한 모든 요청에 공개 주소 판단보다 먼저 적용한다
		if (isSuspiciousPath(exchange.getRequest().getURI().getRawPath())) {
			return reject(exchange.getResponse(), HttpStatus.BAD_REQUEST, CODE_INVALID_PATH, "잘못된 요청 경로입니다.");
		}

		ServerHttpRequest request = exchange.getRequest().mutate()
			.headers(headers -> headers.remove(MEMBER_ID_HEADER))
			.build();

		String token = resolveToken(request);
		if (isPublic(request)) {
			// 공개 주소라도 유효한 토큰이 있으면 회원 ID를 넘긴다 (예: 대회 상세의 "이미 신청함" 표시)
			// 토큰이 없거나 무효하면 로그인하지 않은 요청으로 넘긴다
			return withMemberIdIfValid(request, token)
				.flatMap(forwarded -> chain.filter(exchange.mutate().request(forwarded).build()));
		}

		if (token == null) {
			return reject(exchange.getResponse(), HttpStatus.UNAUTHORIZED, CODE_LOGIN_REQUIRED, "로그인이 필요합니다.");
		}

		JwtClaims claims;
		try {
			claims = jwtProvider.parse(token, TokenType.ACCESS);
		} catch (InvalidTokenException e) {
			// 만료는 클라이언트가 재발급을 시도할 수 있도록 코드와 메시지를 구분한다
			if (e.getReason() == InvalidTokenException.Reason.EXPIRED) {
				return reject(exchange.getResponse(), HttpStatus.UNAUTHORIZED, CODE_TOKEN_EXPIRED, "만료된 토큰입니다.");
			}
			return reject(exchange.getResponse(), HttpStatus.UNAUTHORIZED, CODE_INVALID_TOKEN, "유효하지 않은 토큰입니다.");
		}

		ServerHttpRequest authenticated = request.mutate()
			.header(MEMBER_ID_HEADER, String.valueOf(claims.memberId()))
			.build();
		return isRevoked(claims).flatMap(revoked -> revoked
			? reject(exchange.getResponse(), HttpStatus.UNAUTHORIZED, CODE_INVALID_TOKEN, "로그아웃된 토큰입니다.")
			: chain.filter(exchange.mutate().request(authenticated).build()));
	}

	// 로그아웃한 토큰인지 Redis로 확인한다. Redis가 실패하면 사이트 전체가 멈추지 않도록 통과시킨다 (토큰은 만료 시각에 어차피 끝난다)
	private Mono<Boolean> isRevoked(JwtClaims claims) {
		return redis.hasKey(revokedKey(claims.jti()))
			.timeout(REVOKED_CHECK_TIMEOUT)
			.onErrorResume(e -> {
				log.warn("로그아웃한 토큰 확인에 실패해 통과시킵니다: {}", e.toString());
				return Mono.just(false);
			});
	}

	// 토큰 검사가 다른 필터보다 먼저 일어나도록 가장 앞에 둔다
	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE;
	}

	// 인코딩된 그대로의 경로를 본다. 아래 문자는 서비스 쪽에서 .·/로 풀리거나 떼어져 칸 검사를 피해 갈 수 있으므로 함께 거절한다
	// ;는 Tomcat이 뒤를 떼어 ..;를 ..으로, \는 /로 보는 서버가 있고, %25는 두 번 풀면 %2e가 된다
	private boolean isSuspiciousPath(String rawPath) {
		if (rawPath == null) {
			return false;
		}
		String lowerPath = rawPath.toLowerCase(Locale.ROOT);
		for (String forbidden : FORBIDDEN_PATH_PARTS) {
			if (lowerPath.contains(forbidden)) {
				return true;
			}
		}
		for (String segment : rawPath.split("/")) {
			if (segment.equals("..") || segment.equals(".")) {
				return true;
			}
		}
		return false;
	}

	// CORS 사전 요청(OPTIONS)은 브라우저가 토큰 없이 보내므로 통과시킨다
	// OPTIONS는 API마다 다른 규칙이 아니라 브라우저 동작이라 설정(gateway.auth.public-paths)이 아닌 코드에 둔다
	private boolean isPublic(ServerHttpRequest request) {
		return HttpMethod.OPTIONS.equals(request.getMethod())
			|| publicPaths.matches(request.getMethod(), request.getPath().pathWithinApplication());
	}

	// 로그아웃한 토큰도 무효한 토큰처럼 로그인하지 않은 요청으로 본다
	private Mono<ServerHttpRequest> withMemberIdIfValid(ServerHttpRequest request, String token) {
		if (token == null) {
			return Mono.just(request);
		}
		JwtClaims claims;
		try {
			claims = jwtProvider.parse(token, TokenType.ACCESS);
		} catch (InvalidTokenException e) {
			return Mono.just(request);
		}
		return isRevoked(claims).map(revoked -> revoked
			? request
			: request.mutate().header(MEMBER_ID_HEADER, String.valueOf(claims.memberId())).build());
	}

	private String resolveToken(ServerHttpRequest request) {
		String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
		if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
			return null;
		}
		String token = authorization.substring(BEARER_PREFIX.length()).trim();
		return token.isEmpty() ? null : token;
	}

	// 응답 형식(RsData JSON)은 게이트웨이 오류 응답이 함께 쓰는 RsDataResponseWriter에 둔다
	private Mono<Void> reject(ServerHttpResponse response, HttpStatus status, String code, String message) {
		return RsDataResponseWriter.write(response, status, code, message);
	}
}
