package com.bukang.ai.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 초안을 만들 문의가 없음: 없거나 지운 문의, 남의 대회 문의, 판매자 화면에서 연 관리자 담당 문의 (문의 서비스가 404로 답함)
public class AiInquiryNotFoundException extends BusinessException {
	public AiInquiryNotFoundException() {
		super(HttpStatus.NOT_FOUND, "AI_INQUIRY_NOT_FOUND", "문의를 찾을 수 없습니다.");
	}
}
