package com.bukang.member.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

/**
 * 마지막 로그인 방법은 해제할 수 없음. 비밀번호나 다른 소셜 연결이 남아야 한다 (BE-032)
 */
public class LastLoginMethodException extends BusinessException {
	public static final String CODE = "MEMBER_SOCIAL_LAST_LOGIN_METHOD";

	public LastLoginMethodException(String message) {
		super(HttpStatus.CONFLICT, CODE, message);
	}
}
