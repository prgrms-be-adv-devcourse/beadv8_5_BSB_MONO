package com.bukang.member.app;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Pattern;

import org.springframework.security.crypto.keygen.Base64StringKeyGenerator;
import org.springframework.security.crypto.keygen.StringKeyGenerator;

/**
 * 소셜 로그인이 브라우저를 보낼 프론트 화면 주소를 만든다 (BE-031·032·033)
 * 주소 앞부분(프론트 도메인)은 컨트롤러가 붙인다. 여기서는 앱 안의 경로와 쿼리만 만든다
 */
public final class SocialRedirects {
	public static final String LOGIN_PATH = "/login";
	public static final String COMPLETE_PATH = "/auth/social/complete";
	public static final String SIGNUP_PATH = "/signup/social";
	public static final String ACCOUNTS_PATH = "/me/accounts";
	public static final String HOME_PATH = "/";

	private static final String BASE = "http://crewrun.invalid";
	private static final Pattern WHITESPACE_OR_CONTROL = Pattern.compile("[\\s\\x00-\\x1f\\x7f]");
	// 쿠키·주소에 그대로 실을 수 있는 base64url 문자, 32바이트(256비트) 무작위 값
	private static final StringKeyGenerator RANDOM_IDS =
		new Base64StringKeyGenerator(Base64.getUrlEncoder().withoutPadding(), 32);

	private SocialRedirects() {
	}

	// 인가 요청 ID, 가입 대기 ID, 연결 표. 추측할 수 없어야 하므로 SecureRandom으로 만든다
	public static String randomId() {
		return RANDOM_IDS.generateKey();
	}

	/**
	 * 로그인 뒤 돌아갈 경로를 검사한다. 프론트 safeNextPath(login-redirect.ts)와 같은 규칙이다 (BE-031 결정 5)
	 * 다른 사이트 주소로 보내는 공격(오픈 리다이렉트)을 막는다. 맞지 않으면 fallback을 쓴다
	 * - '/'로 시작해야 한다 ('https://…', 'javascript:…' 같은 스킴이 걸린다)
	 * - '//evil.com', '/\evil.com'은 브라우저가 다른 사이트로 읽으므로 막는다
	 * - 공백·제어 문자는 브라우저가 지워서 '//'가 될 수 있어 막는다
	 * - 로그인 화면 자체로 돌아가는 값은 fallback으로 바꾼다
	 * 뒤에 쿼리를 붙이므로 # 뒤(조각)는 버린다
	 */
	public static String safeNextPath(String next, String fallback) {
		if (next == null || !next.startsWith("/") || next.startsWith("//") || next.startsWith("/\\")) {
			return fallback;
		}
		if (WHITESPACE_OR_CONTROL.matcher(next).find()) {
			return fallback;
		}
		URI uri;
		try {
			uri = new URI(BASE + next);
		} catch (URISyntaxException exception) {
			return fallback;
		}
		if (!"crewrun.invalid".equals(uri.getHost()) || LOGIN_PATH.equals(uri.getRawPath())) {
			return fallback;
		}
		return uri.getRawQuery() == null ? uri.getRawPath() : uri.getRawPath() + "?" + uri.getRawQuery();
	}

	// path 뒤에 쿼리 값 하나를 붙인다. 값은 인코딩한다 (next 안의 ?, & 가 쿼리를 깨지 않게)
	public static String withQuery(String path, String name, String value) {
		String separator = path.contains("?") ? "&" : "?";
		return path + separator + name + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
	}
}
