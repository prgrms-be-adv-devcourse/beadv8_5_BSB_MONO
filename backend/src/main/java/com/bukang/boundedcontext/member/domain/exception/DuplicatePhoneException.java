package com.bukang.boundedcontext.member.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.global.exception.BusinessException;

public class DuplicatePhoneException extends BusinessException {
	public DuplicatePhoneException(String message) {
		super(HttpStatus.CONFLICT, message);
	}
}
