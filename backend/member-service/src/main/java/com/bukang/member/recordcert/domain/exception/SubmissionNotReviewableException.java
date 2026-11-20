package com.bukang.member.recordcert.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 심사 필요가 아닌 제출을 심사하려 할 때. 이미 끝난 결과가 뒤집히지 않게 한다 (BUK-276)
public class SubmissionNotReviewableException extends BusinessException {
	public SubmissionNotReviewableException() {
		super(HttpStatus.CONFLICT, "RECORD_CERT_SUBMISSION_NOT_REVIEWABLE", "심사 필요인 기록증 제출만 심사할 수 있습니다.");
	}
}
