package com.bukang.member.config.auth;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 비밀번호 재설정 설정 (member.auth.password-reset.*, BUK-40)
 * 숫자는 정책 회의 전 초안 값이라 yaml에서 바꿀 수 있게 뺐다
 *
 * @param frontBaseUrl 메일 링크 앞부분 (예: https://crewrun.example). 요청 Host 헤더로 만들지 않고 이 값으로 고정한다
 * @param tokenTtl 재설정 링크를 쓸 수 있는 시간
 * @param requestCooldown 같은 이메일로 다시 요청할 수 있기까지의 시간
 * @param dailyLimit 같은 이메일로 dailyWindow 동안 요청할 수 있는 횟수
 * @param dailyWindow 횟수를 세는 기간
 * @param retryDelays 메일 발송이 실패했을 때 다시 보내기까지 기다리는 시간. 개수만큼 다시 보낸다
 * @param fromName 메일의 보내는 사람 이름. 주소는 spring.mail.username을 쓴다
 */
@ConfigurationProperties("member.auth.password-reset")
public record PasswordResetProperties(
	@DefaultValue("") String frontBaseUrl,
	@DefaultValue("30m") Duration tokenTtl,
	@DefaultValue("1m") Duration requestCooldown,
	@DefaultValue("5") int dailyLimit,
	@DefaultValue("24h") Duration dailyWindow,
	@DefaultValue({"2s", "10s", "30s"}) List<Duration> retryDelays,
	@DefaultValue("크루런") String fromName
) {
	public static final String REQUEST_PATH = "/password-reset";
	public static final String NEW_PASSWORD_PATH = "/password-reset/new";
	public static final String LOGIN_PATH = "/login";

	// 토큰은 주소의 # 뒤에 넣는다. 브라우저가 # 뒤를 서버로 보내지 않아 Nginx 로그와 Referer에 남지 않는다
	public String resetLink(String token) {
		return trimTrailingSlash(frontBaseUrl) + NEW_PASSWORD_PATH + "#token=" + token;
	}

	public String requestPageLink() {
		return trimTrailingSlash(frontBaseUrl) + REQUEST_PATH;
	}

	public String loginLink() {
		return trimTrailingSlash(frontBaseUrl) + LOGIN_PATH;
	}

	private static String trimTrailingSlash(String url) {
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}
}
