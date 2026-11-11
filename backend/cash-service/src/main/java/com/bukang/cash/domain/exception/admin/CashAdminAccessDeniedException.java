package com.bukang.cash.domain.exception.admin;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 관리자 API를 관리자가 아닌 회원이 부름
public class CashAdminAccessDeniedException extends BusinessException {
	public static final String CODE = "CASH_ADMIN_FORBIDDEN";
	public static final String MESSAGE = "관리자만 이용할 수 있습니다.";

	public CashAdminAccessDeniedException() {
		super(HttpStatus.FORBIDDEN, CODE, MESSAGE);
	}
}
