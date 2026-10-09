package com.bukang.member.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

public class MemberNotFoundException extends BusinessException {
	public MemberNotFoundException(String message) {
		super(HttpStatus.NOT_FOUND, message);
	}
}
