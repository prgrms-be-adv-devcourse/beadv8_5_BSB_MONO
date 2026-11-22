package com.bukang.member.recordcert.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 없는 제출 ID (BUK-276)
public class RecordCertSubmissionNotFoundException extends BusinessException {
	public RecordCertSubmissionNotFoundException() {
		super(HttpStatus.NOT_FOUND, "RECORD_CERT_SUBMISSION_NOT_FOUND", "기록증 제출을 찾을 수 없습니다.");
	}
}
