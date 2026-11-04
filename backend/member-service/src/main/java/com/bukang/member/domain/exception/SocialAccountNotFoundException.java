package com.bukang.member.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

/**
 * 연결되지 않은 제공자를 해제하려 함
 */
public class SocialAccountNotFoundException extends BusinessException {
	public static final String CODE = "MEMBER_SOCIAL_ACCOUNT_NOT_FOUND";

	public SocialAccountNotFoundException(String message) {
		super(HttpStatus.NOT_FOUND, CODE, message);
	}
}
