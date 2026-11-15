package com.bukang.cash.domain.exception.admin;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 회원 서비스에 관리자 여부를 묻지 못함 (연결 실패, 응답 시간 초과, 5xx, 응답 형식 오류)
public class MemberServiceUnavailableException extends BusinessException {
	public static final String CODE = "CASH_MEMBER_SERVICE_UNAVAILABLE";
	public static final String MESSAGE = "회원 서비스에 연결하지 못했습니다. 잠시 뒤 다시 시도해주세요.";

	public MemberServiceUnavailableException() {
		super(HttpStatus.SERVICE_UNAVAILABLE, CODE, MESSAGE);
	}
}
