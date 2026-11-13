package com.bukang.inquiry.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 다른 서비스(회원·상품)에 연결하지 못함 (연결 실패, 응답 시간 초과, 5xx, 응답 형식 오류)
public class InquiryExternalServiceUnavailableException extends BusinessException {
	public static final String CODE = "INQUIRY_EXTERNAL_SERVICE_UNAVAILABLE";

	public InquiryExternalServiceUnavailableException(String message) {
		super(HttpStatus.SERVICE_UNAVAILABLE, CODE, message);
	}
}
