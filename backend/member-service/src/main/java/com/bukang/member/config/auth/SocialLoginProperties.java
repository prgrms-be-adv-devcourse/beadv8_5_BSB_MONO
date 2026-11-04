package com.bukang.member.config.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 소셜 로그인 설정 (member.auth.social.*, BE-031). 키 값은 .env에만 두고 yaml은 환경 변수 이름만 적는다
 *
 * @param redirectBaseUrl 콜백 주소 앞부분 (예: https://crewrun.example). 제공자 앱에 <이 값>/api/v1/auth/oauth2/callback/kakao를 등록한다
 * @param frontBaseUrl 콜백 뒤 보낼 프론트 주소 앞부분. 비우면 같은 도메인의 상대 주소(/login 등)로 보낸다
 * @param kakao 키가 비어 있으면 카카오 로그인을 등록하지 않는다 (앱은 뜨고, 시작하면 실패 화면으로 보낸다)
 */
@ConfigurationProperties("member.auth.social")
public record SocialLoginProperties(
	@DefaultValue("http://localhost") String redirectBaseUrl,
	@DefaultValue("") String frontBaseUrl,
	@DefaultValue Kakao kakao,
	@DefaultValue Google google
) {
	public static final String CALLBACK_PATH = "/api/v1/auth/oauth2/callback/";

	public String callbackUri(String pathValue) {
		return trimTrailingSlash(redirectBaseUrl) + CALLBACK_PATH + pathValue;
	}

	public String frontUrl(String pathAndQuery) {
		return trimTrailingSlash(frontBaseUrl) + pathAndQuery;
	}

	private static String trimTrailingSlash(String url) {
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}

	/**
	 * @param adminKey 탈퇴·연결 해제 때 카카오 연결 끊기에 쓴다 (BE-034). 비우면 연결 끊기를 건너뛴다
	 */
	public record Kakao(@DefaultValue("") String clientId, @DefaultValue("") String clientSecret,
		@DefaultValue("") String adminKey) {
		public boolean isConfigured() {
			return !clientId.isBlank() && !clientSecret.isBlank();
		}
	}

	public record Google(@DefaultValue("") String clientId, @DefaultValue("") String clientSecret) {
		public boolean isConfigured() {
			return !clientId.isBlank() && !clientSecret.isBlank();
		}
	}
}
