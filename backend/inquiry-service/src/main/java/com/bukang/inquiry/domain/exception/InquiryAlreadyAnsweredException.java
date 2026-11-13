package com.bukang.inquiry.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 이미 답변이 달린 문의에 다시 답하려 했다. 답변은 한 번만 달 수 있다
public class InquiryAlreadyAnsweredException extends BusinessException {
	public InquiryAlreadyAnsweredException() {
		super(HttpStatus.CONFLICT, "INQUIRY_ALREADY_ANSWERED", "이미 답변한 문의입니다.");
	}
}
