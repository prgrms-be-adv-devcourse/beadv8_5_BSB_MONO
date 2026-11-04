package com.bukang.member.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 수신 거부 토큰 서명 오류 (BUK-75, BE-039)
public class UnsubscribeTokenInvalidException extends BusinessException {
	public UnsubscribeTokenInvalidException(String message) {
		super(HttpStatus.UNAUTHORIZED, "MEMBER_UNSUBSCRIBE_TOKEN_INVALID", message);
	}
}
