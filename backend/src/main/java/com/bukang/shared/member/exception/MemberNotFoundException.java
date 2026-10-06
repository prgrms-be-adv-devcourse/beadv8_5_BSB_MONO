package com.bukang.shared.member.exception;

import org.springframework.http.HttpStatus;

import com.bukang.global.exception.BusinessException;

public class MemberNotFoundException extends BusinessException {
	public MemberNotFoundException(String message) {
		super(HttpStatus.NOT_FOUND, message);
	}
}
