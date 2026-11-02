package com.bukang.member.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

/**
 * 본인인증 결과가 없거나 15분이 지났다. 본인인증을 처음부터 다시 한다 (BUK-33)
 */
public class IdentityVerificationRequiredException extends BusinessException {
	public static final String CODE = "AUTH_VERIFICATION_REQUIRED";

	public IdentityVerificationRequiredException(String message) {
		super(HttpStatus.BAD_REQUEST, CODE, message);
	}
}
