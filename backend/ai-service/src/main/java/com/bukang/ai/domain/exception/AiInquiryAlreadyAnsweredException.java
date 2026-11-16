package com.bukang.ai.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 이미 답변한 문의라 초안을 만들지 않음 (답변은 한 번만 달 수 있어 초안을 써도 보낼 수 없다)
public class AiInquiryAlreadyAnsweredException extends BusinessException {
	public AiInquiryAlreadyAnsweredException() {
		super(HttpStatus.CONFLICT, "AI_INQUIRY_ALREADY_ANSWERED", "이미 답변한 문의입니다.");
	}
}
