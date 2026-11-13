package com.bukang.inquiry.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 문의를 남길 대회가 없다
public class InquiryRaceNotFoundException extends BusinessException {
	public InquiryRaceNotFoundException() {
		super(HttpStatus.NOT_FOUND, "INQUIRY_RACE_NOT_FOUND", "대회를 찾을 수 없습니다.");
	}
}
