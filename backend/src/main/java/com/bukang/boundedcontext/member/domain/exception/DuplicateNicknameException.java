package com.bukang.boundedcontext.member.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.global.exception.BusinessException;

public class DuplicateNicknameException extends BusinessException {
	public DuplicateNicknameException(String message) {
		super(HttpStatus.CONFLICT, message);
	}
}
