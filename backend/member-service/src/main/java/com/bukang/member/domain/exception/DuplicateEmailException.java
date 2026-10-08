package com.bukang.member.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

public class DuplicateEmailException extends BusinessException {
	public DuplicateEmailException(String message) {
		super(HttpStatus.CONFLICT, message);
	}
}
