package com.bukang.member.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

/**
 * 인증사가 CI를 주지 않았다(KG이니시스 통합인증의 카카오 인증). CI가 없으면 1인 1계정을 확인할 수 없어 다른 인증사를 고르게 한다 (BUK-33)
 */
public class IdentityCiMissingException extends BusinessException {
	public static final String CODE = "AUTH_VERIFICATION_CI_MISSING";

	public IdentityCiMissingException(String message) {
		super(HttpStatus.BAD_REQUEST, CODE, message);
	}
}
