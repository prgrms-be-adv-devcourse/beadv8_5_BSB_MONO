package com.bukang.member.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

public class UnderAgeException extends BusinessException {
	public UnderAgeException(String message) {
		super(HttpStatus.BAD_REQUEST, message);
	}
}
