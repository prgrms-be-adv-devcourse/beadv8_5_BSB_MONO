package com.bukang.member.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

/**
 * 그 소셜 계정이 이미 다른 회원에 연결됨 (BE-032)
 */
public class SocialAccountAlreadyLinkedException extends BusinessException {
	public static final String CODE = "AUTH_SOCIAL_ALREADY_LINKED";

	public SocialAccountAlreadyLinkedException(String message) {
		super(HttpStatus.CONFLICT, CODE, message);
	}
}
