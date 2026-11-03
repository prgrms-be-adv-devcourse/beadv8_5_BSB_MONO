package com.bukang.member.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

/**
 * 재설정 링크가 없거나, 30분이 지났거나, 이미 썼거나, 더 새 링크가 나와 무효가 됨 (BUK-40)
 * 어느 경우인지 나누지 않는다. 사용자는 모두 다시 요청하면 된다
 */
public class PasswordResetTokenInvalidException extends BusinessException {
	public static final String CODE = "AUTH_RESET_TOKEN_INVALID";

	public PasswordResetTokenInvalidException(String message) {
		super(HttpStatus.BAD_REQUEST, CODE, message);
	}
}
