package com.bukang.member.app;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.bukang.member.domain.exception.EmailCheckTooManyRequestsException;
import com.bukang.member.out.emailcheck.EmailCheckStore;

import lombok.RequiredArgsConstructor;

/**
 * 로그인 없이 부르는 이메일 중복 확인을 같은 IP 기준 1분에 30번으로 제한한다 (BUK-186)
 * Redis 오류는 그대로 올려 실패시킨다. 가입 자체는 서버의 유니크 제약이 막는다
 */
@Component
@RequiredArgsConstructor
public class EmailCheckLimiter {
	static final String TOO_MANY_MESSAGE = "요청이 너무 많습니다. 잠시 뒤 다시 시도해주세요.";
	private static final int LIMIT = 30;
	private static final Duration WINDOW = Duration.ofMinutes(1);

	private final EmailCheckStore store;

	public void check(String clientIp) {
		if (!store.tryAcquire(Sha256.hex(clientIp), LIMIT, WINDOW)) {
			throw new EmailCheckTooManyRequestsException(TOO_MANY_MESSAGE);
		}
	}
}
