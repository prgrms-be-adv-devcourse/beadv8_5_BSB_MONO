package com.bukang.member.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

public class DuplicatePhoneException extends BusinessException {
	public DuplicatePhoneException(String message) {
		super(HttpStatus.CONFLICT, message);
	}
}
