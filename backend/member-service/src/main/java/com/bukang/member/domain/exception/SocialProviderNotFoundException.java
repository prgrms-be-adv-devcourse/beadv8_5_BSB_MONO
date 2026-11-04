package com.bukang.member.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

/**
 * 지원하지 않는 소셜 로그인 제공자 (kakao, google 말고 다른 값)
 */
public class SocialProviderNotFoundException extends BusinessException {
	public static final String CODE = "AUTH_SOCIAL_PROVIDER_NOT_FOUND";

	public SocialProviderNotFoundException(String message) {
		super(HttpStatus.NOT_FOUND, CODE, message);
	}
}
