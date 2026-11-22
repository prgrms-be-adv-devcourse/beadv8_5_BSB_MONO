package com.bukang.member.recordcert.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 제출에 저장된 판매자 회원 ID가 로그인 회원과 다를 때 (남의 대회) (BUK-276)
public class RecordCertNotOwnerException extends BusinessException {
	public RecordCertNotOwnerException() {
		super(HttpStatus.FORBIDDEN, "RECORD_CERT_NOT_OWNER", "내 대회의 기록증 제출이 아닙니다.");
	}
}
