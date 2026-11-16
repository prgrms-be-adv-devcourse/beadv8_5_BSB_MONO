package com.bukang.member.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 같은 IP가 이메일 중복 확인을 너무 자주 불렀다 (BUK-186)
public class EmailCheckTooManyRequestsException extends BusinessException {
	public static final String CODE = "EMAIL_CHECK_TOO_MANY";

	public EmailCheckTooManyRequestsException(String message) {
		super(HttpStatus.TOO_MANY_REQUESTS, CODE, message);
	}
}
