package com.bukang.common.shared.member.web;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

/**
 * 회원 ID 헤더가 숫자가 아니거나 0 이하임. 입력값 오류와 같은 코드(INVALID_REQUEST)를 쓴다
 */
public class InvalidMemberIdException extends BusinessException {
	public static final String CODE = "INVALID_REQUEST";

	public InvalidMemberIdException() {
		super(HttpStatus.BAD_REQUEST, CODE, MemberIdHeader.INVALID_MESSAGE);
	}
}
