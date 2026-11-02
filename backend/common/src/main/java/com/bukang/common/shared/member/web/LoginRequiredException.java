package com.bukang.common.shared.member.web;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

/**
 * 회원 ID 헤더가 없음. 게이트웨이는 Access Token을 확인했을 때만 이 헤더를 넣으므로 로그인하지 않은 요청이다
 */
public class LoginRequiredException extends BusinessException {
	public static final String CODE = "LOGIN_REQUIRED";

	public LoginRequiredException() {
		super(HttpStatus.UNAUTHORIZED, CODE, "로그인이 필요합니다.");
	}
}
