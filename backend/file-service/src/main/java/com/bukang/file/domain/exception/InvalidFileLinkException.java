package com.bukang.file.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

/**
 * 연결 요청 값이 올바르지 않을 때 (대상 종류·ID 형식, 같은 파일 중복)
 */
public class InvalidFileLinkException extends BusinessException {
	public InvalidFileLinkException(String message) {
		super(HttpStatus.BAD_REQUEST, message);
	}
}
