package com.bukang.member.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

/**
 * 같은 이메일로 재설정을 너무 자주 요청함 (BUK-40). 가입하지 않은 이메일에도 똑같이 걸어 가입 여부가 드러나지 않는다
 */
public class PasswordResetTooManyRequestsException extends BusinessException {
	public static final String CODE = "AUTH_RESET_TOO_MANY";

	public PasswordResetTooManyRequestsException(String message) {
		super(HttpStatus.TOO_MANY_REQUESTS, CODE, message);
	}
}
