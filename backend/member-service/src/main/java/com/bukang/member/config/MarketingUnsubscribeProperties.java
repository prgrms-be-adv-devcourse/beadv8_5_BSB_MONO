package com.bukang.member.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * CRM 광고 수신 거부 링크 설정 (member.marketing.unsubscribe.*, BUK-75, BE-039). 키 값은 .env에만 둔다
 *
 * @param secretKey 토큰 서명 키. 회원 서비스 밖으로 내보내지 않는다
 * @param frontBaseUrl 링크 앞부분 (예: https://crewrun.example). 요청 Host 헤더로 만들지 않고 이 값으로 고정한다
 * @param tokenTtl 링크를 쓸 수 있는 기간. 광고를 늦게 열어도 거부할 수 있게 길게 둔다
 */
@ConfigurationProperties("member.marketing.unsubscribe")
public record MarketingUnsubscribeProperties(
	@DefaultValue("") String secretKey,
	@DefaultValue("") String frontBaseUrl,
	@DefaultValue("365d") Duration tokenTtl
) {
	public static final String CONFIRM_PATH = "/unsubscribe";

	// 확인 화면이 주소의 token을 읽어 버튼을 누를 때 공개 API로 보낸다. 링크를 여는 것만으로는 끄지 않는다
	public String link(String token) {
		// 앞부분이 없으면 메시지 안에서 열 수 없는 상대 주소가 되므로 발급하지 않는다. 서버 설정 문제라 500으로 둔다
		if (frontBaseUrl.isBlank()) {
			throw new IllegalStateException("수신 거부 링크 앞부분(FRONT_PUBLIC_BASE_URL)이 설정되지 않았습니다.");
		}
		String base = frontBaseUrl.endsWith("/") ? frontBaseUrl.substring(0, frontBaseUrl.length() - 1) : frontBaseUrl;
		return base + CONFIRM_PATH + "?token=" + token;
	}
}
