package com.bukang.member.config.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 로그인 실패 제한 설정 (member.auth.login-attempt.*, BUK-185)
 *
 * @param maxFailures 이만큼 틀리면 막는다. 그다음 시도부터 비밀번호가 맞아도 거절한다
 * @param blockDuration 마지막으로 실패한 때부터 막는 시간
 */
@ConfigurationProperties("member.auth.login-attempt")
public record LoginAttemptProperties(
	@DefaultValue("5") int maxFailures,
	@DefaultValue("10m") Duration blockDuration
) {
}
