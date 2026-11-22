package com.bukang.member.config.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * QA용 테스트 회원 만들기 설정 (member.auth.test-member.*)
 *
 * @param enabled 켜졌을 때만 테스트 회원 만들기 주소를 만든다. 기본은 꺼짐이다
 */
@ConfigurationProperties("member.auth.test-member")
public record TestMemberProperties(
	@DefaultValue("false") boolean enabled
) {
}
