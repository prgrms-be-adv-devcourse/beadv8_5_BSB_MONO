package com.bukang.file.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

/**
 * 다른 회원이 올린 파일을 처리하려 할 때
 */
public class FileAccessDeniedException extends BusinessException {
	public FileAccessDeniedException(String message) {
		super(HttpStatus.FORBIDDEN, message);
	}
}
