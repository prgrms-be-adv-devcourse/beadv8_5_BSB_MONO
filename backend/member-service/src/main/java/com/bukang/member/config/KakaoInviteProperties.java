package com.bukang.member.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 크루 초대 카카오톡 메시지 설정 (member.crew.kakao-invite.*, kakao-crew-invite 결정 3)
 *
 * @param frontBaseUrl 메시지 버튼 링크 앞부분 (예: https://crewrun.example). 카카오 콘솔 「웹 도메인」에 등록한 도메인이어야
 *                     버튼이 동작한다. 요청 Host 헤더로 만들지 않는다
 */
@ConfigurationProperties("member.crew.kakao-invite")
public record KakaoInviteProperties(@DefaultValue("") String frontBaseUrl) {
	public String inviteUrl(String inviteToken) {
		String base = frontBaseUrl.endsWith("/") ? frontBaseUrl.substring(0, frontBaseUrl.length() - 1) : frontBaseUrl;
		return base + "/invite/" + inviteToken;
	}
}
