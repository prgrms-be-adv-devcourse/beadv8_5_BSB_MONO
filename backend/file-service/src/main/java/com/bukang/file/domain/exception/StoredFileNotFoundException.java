package com.bukang.file.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

public class StoredFileNotFoundException extends BusinessException {
	public StoredFileNotFoundException(String message) {
		super(HttpStatus.NOT_FOUND, message);
	}
}
