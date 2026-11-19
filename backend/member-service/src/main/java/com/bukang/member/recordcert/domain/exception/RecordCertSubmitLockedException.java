package com.bukang.member.recordcert.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 같은 참가권의 제출이 동시에 들어와 두 번째가 잠금을 못 잡았다 (BUK-274)
public class RecordCertSubmitLockedException extends BusinessException {
	public RecordCertSubmitLockedException() {
		super(HttpStatus.CONFLICT, "RECORD_CERT_SUBMIT_LOCKED", "같은 참가권의 제출을 처리하고 있습니다. 잠시 뒤 다시 시도해주세요.");
	}
}
