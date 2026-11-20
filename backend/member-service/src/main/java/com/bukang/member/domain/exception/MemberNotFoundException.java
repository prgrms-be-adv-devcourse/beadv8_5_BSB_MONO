package com.bukang.member.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

public class MemberNotFoundException extends BusinessException {
	public MemberNotFoundException() {
		this("존재하지 않는 회원입니다.");
	}

	public MemberNotFoundException(String message) {
		super(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", message);
	}
}
