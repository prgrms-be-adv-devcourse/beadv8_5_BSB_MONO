package com.bukang.member.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

/**
 * 제공자가 확인된 이메일을 주지 않았는데 가입 요청에도 이메일이 없음 (BE-033)
 */
public class SocialEmailRequiredException extends BusinessException {
	public static final String CODE = "AUTH_SOCIAL_EMAIL_REQUIRED";

	public SocialEmailRequiredException(String message) {
		super(HttpStatus.BAD_REQUEST, CODE, message);
	}
}
