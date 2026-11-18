package com.bukang.member.recordcert.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 확인 중이 아닌 제출에 서버 확인 · 판정 결과를 다시 반영하려 할 때 (두 파드가 같은 제출을 처리하는 경우 등)
public class SubmissionNotCheckingException extends BusinessException {
	public SubmissionNotCheckingException() {
		super(HttpStatus.CONFLICT, "RECORD_CERT_SUBMISSION_NOT_CHECKING", "확인 중인 기록증 제출이 아닙니다.");
	}
}
