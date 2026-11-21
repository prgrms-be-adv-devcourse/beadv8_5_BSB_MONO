package com.bukang.member.recordcert.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 파일 서비스가 기록증 사진 보기 주소를 주지 못했을 때 (BUK-276)
public class RecordCertFileViewUnavailableException extends BusinessException {
	public RecordCertFileViewUnavailableException() {
		super(HttpStatus.SERVICE_UNAVAILABLE, "RECORD_CERT_FILE_VIEW_UNAVAILABLE",
			"기록증 사진을 불러오지 못했습니다. 잠시 뒤 다시 시도해주세요.");
	}
}
