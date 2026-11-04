package com.bukang.member.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 수신 거부 토큰 기한 오류 (BUK-75, BE-039)
public class UnsubscribeTokenExpiredException extends BusinessException {
	public UnsubscribeTokenExpiredException(String message) {
		super(HttpStatus.GONE, "MEMBER_UNSUBSCRIBE_TOKEN_EXPIRED", message);
	}
}
