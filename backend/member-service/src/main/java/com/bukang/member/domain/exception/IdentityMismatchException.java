package com.bukang.member.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

/**
 * 본인인증한 사람이 로그인한 회원 본인이 아니다 (BUK-71, BE-037). 휴대폰 번호를 바꾸지 않는다
 */
public class IdentityMismatchException extends BusinessException {
	public static final String CODE = "MEMBER_IDENTITY_MISMATCH";

	public IdentityMismatchException(String message) {
		super(HttpStatus.FORBIDDEN, CODE, message);
	}
}
