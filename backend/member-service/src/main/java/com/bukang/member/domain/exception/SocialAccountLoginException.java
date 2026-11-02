package com.bukang.member.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

/**
 * 비밀번호가 없는 소셜 회원이 이메일로 로그인함. 문구로 가입한 제공자를 알려 준다 (BE-035)
 */
public class SocialAccountLoginException extends BusinessException {
	public static final String CODE = "AUTH_SOCIAL_ACCOUNT";

	public SocialAccountLoginException(String message) {
		super(HttpStatus.UNAUTHORIZED, CODE, message);
	}
}
