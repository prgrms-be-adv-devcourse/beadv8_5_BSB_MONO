package com.bukang.inquiry.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 없는 문의이거나 볼 권한이 없는 문의다. 남의 문의가 있는지도 알려 주지 않도록 둘 다 404로 답한다
public class InquiryNotFoundException extends BusinessException {
	public InquiryNotFoundException() {
		super(HttpStatus.NOT_FOUND, "INQUIRY_NOT_FOUND", "문의를 찾을 수 없습니다.");
	}
}
