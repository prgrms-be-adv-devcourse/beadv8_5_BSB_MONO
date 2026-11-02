package com.bukang.member.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

/**
 * 소셜 가입 대기(Redis 15분)가 없거나 지남. 소셜 로그인부터 다시 한다 (BE-033)
 */
public class SocialSignupExpiredException extends BusinessException {
	public static final String CODE = "AUTH_SOCIAL_SIGNUP_EXPIRED";

	public SocialSignupExpiredException(String message) {
		super(HttpStatus.UNAUTHORIZED, CODE, message);
	}
}
