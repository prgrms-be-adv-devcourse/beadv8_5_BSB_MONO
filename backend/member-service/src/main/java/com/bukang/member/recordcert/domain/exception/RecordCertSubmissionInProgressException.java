package com.bukang.member.recordcert.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 같은 참가권에 확인 중 · 심사 필요 · 자동 승인 · 승인인 제출이 있다 (BUK-274)
public class RecordCertSubmissionInProgressException extends BusinessException {
	public RecordCertSubmissionInProgressException() {
		super(HttpStatus.CONFLICT, "RECORD_CERT_SUBMISSION_IN_PROGRESS", "이 참가권으로 낸 기록증이 이미 확인 중이거나 승인됐습니다.");
	}
}
