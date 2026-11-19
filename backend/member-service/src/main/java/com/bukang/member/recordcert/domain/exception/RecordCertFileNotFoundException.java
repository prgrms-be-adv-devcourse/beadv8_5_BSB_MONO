package com.bukang.member.recordcert.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// file에 없거나 삭제된 사진 (BUK-274)
public class RecordCertFileNotFoundException extends BusinessException {
	public RecordCertFileNotFoundException() {
		super(HttpStatus.NOT_FOUND, "RECORD_CERT_FILE_NOT_FOUND", "기록증 사진을 찾을 수 없습니다. 사진을 다시 올려 주세요.");
	}
}
