package com.bukang.member.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

/**
 * 같은 사람(CI 해시)이 이미 가입했다. 화면은 로그인이나 계정 연결을 안내한다 (BUK-33)
 */
public class DuplicateIdentityException extends BusinessException {
	public static final String CODE = "MEMBER_DUPLICATE_IDENTITY";

	public DuplicateIdentityException(String message) {
		super(HttpStatus.CONFLICT, CODE, message);
	}
}
