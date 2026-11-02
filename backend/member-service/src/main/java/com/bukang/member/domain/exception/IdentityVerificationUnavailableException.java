package com.bukang.member.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

/**
 * 포트원 조회가 실패했다(연결 실패, 응답 지연, 인증 키 오류). 사용자가 잘못한 것이 아니라 잠시 뒤 다시 시도하게 한다 (BUK-33)
 */
public class IdentityVerificationUnavailableException extends BusinessException {
	public static final String CODE = "AUTH_VERIFICATION_UNAVAILABLE";

	public IdentityVerificationUnavailableException(String message) {
		super(HttpStatus.SERVICE_UNAVAILABLE, CODE, message);
	}
}
