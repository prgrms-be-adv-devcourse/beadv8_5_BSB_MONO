package com.bukang.file.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

/**
 * 받을 수 없는 파일 (형식, 크기, 업로드 안 됨 등)
 */
public class InvalidFileException extends BusinessException {
	public InvalidFileException(String message) {
		super(HttpStatus.BAD_REQUEST, message);
	}
}
