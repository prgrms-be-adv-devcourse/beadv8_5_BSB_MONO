package com.bukang.member.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

/**
 * 포트원 조회 결과가 인증 완료(VERIFIED)가 아니다. 사용자가 인증 창을 닫았거나 인증에 실패했다 (BUK-33)
 */
public class IdentityVerificationFailedException extends BusinessException {
	public static final String CODE = "AUTH_VERIFICATION_FAILED";

	public IdentityVerificationFailedException(String message) {
		super(HttpStatus.BAD_REQUEST, CODE, message);
	}
}
