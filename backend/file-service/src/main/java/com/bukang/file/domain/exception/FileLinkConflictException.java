package com.bukang.file.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

/**
 * 지금 상태로는 대상에 연결할 수 없는 파일 (업로드 확인 전, 다른 대상에 연결됨)
 */
public class FileLinkConflictException extends BusinessException {
	public FileLinkConflictException(String message) {
		super(HttpStatus.CONFLICT, message);
	}
}
