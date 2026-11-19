package com.bukang.member.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 같은 IP가 데모 로그인을 너무 자주 불렀다
public class DemoLoginTooManyRequestsException extends BusinessException {
	public static final String CODE = "DEMO_LOGIN_TOO_MANY";

	public DemoLoginTooManyRequestsException(String message) {
		super(HttpStatus.TOO_MANY_REQUESTS, CODE, message);
	}
}
