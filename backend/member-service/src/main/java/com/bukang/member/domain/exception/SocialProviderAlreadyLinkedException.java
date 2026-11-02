package com.bukang.member.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

/**
 * 회원에 같은 제공자가 이미 연결됨. 한 회원에 제공자마다 하나만 연결한다 (BE-035)
 */
public class SocialProviderAlreadyLinkedException extends BusinessException {
	public static final String CODE = "MEMBER_SOCIAL_PROVIDER_ALREADY_LINKED";

	public SocialProviderAlreadyLinkedException(String message) {
		super(HttpStatus.CONFLICT, CODE, message);
	}
}
