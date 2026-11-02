package com.bukang.member.in;

import java.time.Duration;

import org.springframework.http.ResponseCookie;

/**
 * 인증 쿠키 (이메일 로그인 ApiV1AuthController와 소셜 로그인 ApiV1SocialAuthController가 함께 쓴다, BE-036)
 * httpOnly: 자바스크립트에서 읽지 못하게 해 XSS로 값이 새는 것을 막는다
 * SameSite=Lax: 다른 사이트에서 보낸 POST에는 실리지 않는다. 제공자 화면에서 돌아오는 페이지 이동(GET)에는 실린다
 * maxAge가 0이면 브라우저가 쿠키를 지운다. 지우려면 이름·경로가 발급할 때와 같아야 한다
 */
final class AuthCookies {
	// Refresh Token 쿠키는 토큰 재발급 등 인증 API에만 실려 가도록 경로를 좁힌다
	static final String REFRESH_TOKEN = "refreshToken";
	static final String REFRESH_TOKEN_PATH = "/api/v1/auth";
	// 소셜 로그인 도중의 쿠키는 소셜 로그인 API에만 실린다 (BE-031·033)
	static final String OAUTH2_REQUEST = "oauth2Request";
	static final String OAUTH2_SIGNUP = "oauth2Signup";
	static final String OAUTH2_PATH = "/api/v1/auth/oauth2";
	static final Duration OAUTH2_REQUEST_AGE = Duration.ofMinutes(10);
	static final Duration OAUTH2_SIGNUP_AGE = Duration.ofMinutes(15);
	// 계정 연결 표는 시작 API에만 실린다. 표를 받은 브라우저에서 시작할 때만 연결로 처리하려고 쿠키로 준다 (BE-032)
	static final String OAUTH2_LINK = "oauth2Link";
	static final String OAUTH2_LINK_PATH = "/api/v1/auth/oauth2/authorize";
	static final Duration OAUTH2_LINK_AGE = Duration.ofMinutes(2);

	private AuthCookies() {
	}

	static ResponseCookie refreshToken(String value, Duration maxAge) {
		return cookie(REFRESH_TOKEN, value, REFRESH_TOKEN_PATH, maxAge);
	}

	static ResponseCookie oauth2Request(String requestId) {
		return cookie(OAUTH2_REQUEST, requestId, OAUTH2_PATH, OAUTH2_REQUEST_AGE);
	}

	static ResponseCookie oauth2Signup(String pendingId) {
		return cookie(OAUTH2_SIGNUP, pendingId, OAUTH2_PATH, OAUTH2_SIGNUP_AGE);
	}

	static ResponseCookie oauth2Link(String ticket) {
		return cookie(OAUTH2_LINK, ticket, OAUTH2_LINK_PATH, OAUTH2_LINK_AGE);
	}

	static ResponseCookie expired(String name, String path) {
		return cookie(name, "", path, Duration.ZERO);
	}

	private static ResponseCookie cookie(String name, String value, String path, Duration maxAge) {
		return ResponseCookie.from(name, value)
			.httpOnly(true)
			.secure(true)
			.sameSite("Lax")
			.path(path)
			.maxAge(maxAge)
			.build();
	}
}
