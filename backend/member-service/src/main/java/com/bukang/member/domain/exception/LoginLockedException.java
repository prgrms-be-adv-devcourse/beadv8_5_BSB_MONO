package com.bukang.member.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

/**
 * 같은 이메일로 로그인을 여러 번 실패해 잠시 막힘 (BUK-185). 가입하지 않은 이메일에도 똑같이 걸어 가입 여부가 드러나지 않는다
 */
public class LoginLockedException extends BusinessException {
	public static final String CODE = "AUTH_LOGIN_LOCKED";

	public LoginLockedException(String message) {
		super(HttpStatus.TOO_MANY_REQUESTS, CODE, message);
	}
}
