package com.bukang.member.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

public class MemberNicknameLookupTooManyException extends BusinessException {
	public MemberNicknameLookupTooManyException(int max) {
		super(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "회원 ID는 한 번에 %d개까지 조회할 수 있습니다.".formatted(max));
	}
}
