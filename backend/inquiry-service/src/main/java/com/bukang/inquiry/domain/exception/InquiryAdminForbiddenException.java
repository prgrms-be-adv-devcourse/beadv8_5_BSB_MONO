package com.bukang.inquiry.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 관리자가 아닌 회원이 관리자 주소를 불렀다
public class InquiryAdminForbiddenException extends BusinessException {
	public InquiryAdminForbiddenException() {
		super(HttpStatus.FORBIDDEN, "INQUIRY_ADMIN_FORBIDDEN", "관리자만 이용할 수 있습니다.");
	}
}
