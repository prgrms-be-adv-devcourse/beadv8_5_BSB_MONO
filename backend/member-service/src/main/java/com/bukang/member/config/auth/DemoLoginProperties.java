package com.bukang.member.config.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 발표 시연용 데모 로그인 설정 (member.auth.demo-login.*)
 *
 * @param enabled 켜졌을 때만 데모 로그인 주소를 만든다. 기본은 꺼짐이다
 * @param email 데모 로그인으로 로그인시키는 계정 하나. 요청으로는 바꿀 수 없다
 */
@ConfigurationProperties("member.auth.demo-login")
public record DemoLoginProperties(
	@DefaultValue("false") boolean enabled,
	@DefaultValue("demo@crewrun.com") String email
) {
}
