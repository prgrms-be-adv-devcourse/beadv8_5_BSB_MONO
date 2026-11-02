package com.bukang.member.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

/**
 * 소셜 로그인 실패 (동의 취소, state 불일치, 제공자 응답 오류, 키 미설정). 응답으로 나가지 않고 화면 이동의 error 값이 된다
 */
public class SocialLoginFailedException extends BusinessException {
	public static final String CODE = "AUTH_SOCIAL_FAILED";

	public SocialLoginFailedException(String message) {
		super(HttpStatus.BAD_REQUEST, CODE, message);
	}
}
