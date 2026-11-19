package com.bukang.file.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 내부 파일 삭제 API에서 S3 객체를 지우지 못했다. 행은 남아 있어 다시 부르면 이어서 지운다
public class FileDeleteFailedException extends BusinessException {
	public FileDeleteFailedException() {
		super(HttpStatus.SERVICE_UNAVAILABLE, "파일을 지우지 못했습니다. 잠시 뒤 다시 시도해주세요.");
	}
}
