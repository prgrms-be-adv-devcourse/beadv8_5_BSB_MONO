package com.bukang.member.recordcert.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 남이 올린 사진. 남의 기록으로 자동 승인되지 않게 막는다 (BUK-274)
public class RecordCertFileForbiddenException extends BusinessException {
	public RecordCertFileForbiddenException() {
		super(HttpStatus.FORBIDDEN, "RECORD_CERT_FILE_FORBIDDEN", "내가 올린 기록증 사진으로만 제출할 수 있습니다.");
	}
}
