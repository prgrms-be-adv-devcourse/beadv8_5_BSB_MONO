package com.bukang.order.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 관리자 API를 관리자가 아닌 회원이 부름 (BUK-26)
public class AdminAccessDeniedException extends BusinessException {
	public static final String CODE = "ORDER_ADMIN_FORBIDDEN";

	public AdminAccessDeniedException(String message) {
		super(HttpStatus.FORBIDDEN, CODE, message);
	}
}
