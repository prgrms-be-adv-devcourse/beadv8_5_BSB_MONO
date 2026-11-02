package com.bukang.member.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

/**
 * 소셜 가입에서 휴대폰 번호가 이미 다른 회원 것이다. 같은 사람이 다른 이메일의 소셜 계정으로 다시 가입하려는 경우다 (BE-033)
 * 화면은 「원래 방법으로 로그인한 뒤 계정 연결」을 안내한다. 가입 방법은 알려 주지 않는다(번호로 남의 가입 방법을 알아낼 수 없게)
 */
public class SocialPhoneTakenException extends BusinessException {
	public static final String CODE = "AUTH_SOCIAL_PHONE_TAKEN";

	public SocialPhoneTakenException(String message) {
		super(HttpStatus.CONFLICT, CODE, message);
	}
}
