package com.bukang.member.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 수신 거부 토큰 형식 오류 (BUK-75, BE-039)
public class UnsubscribeTokenMalformedException extends BusinessException {
	public UnsubscribeTokenMalformedException(String message) {
		super(HttpStatus.BAD_REQUEST, "MEMBER_UNSUBSCRIBE_TOKEN_MALFORMED", message);
	}
}
