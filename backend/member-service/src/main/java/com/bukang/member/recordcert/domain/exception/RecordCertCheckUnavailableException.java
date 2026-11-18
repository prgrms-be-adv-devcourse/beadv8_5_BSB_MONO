package com.bukang.member.recordcert.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// order · file · product · Redis가 응답하지 않으면 기록증 제출을 저장하지 않는다. 막는 쪽으로 실패한다 (BUK-274)
public class RecordCertCheckUnavailableException extends BusinessException {
	public RecordCertCheckUnavailableException(String message) {
		super(HttpStatus.SERVICE_UNAVAILABLE, "RECORD_CERT_CHECK_UNAVAILABLE", message);
	}
}
