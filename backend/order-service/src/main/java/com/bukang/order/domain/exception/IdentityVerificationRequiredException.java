package com.bukang.order.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 참가자 이름·생년월일·휴대폰은 회원 본인인증 값으로 채우므로, 값이 빈 회원은 본인인증부터 하게 한다 (CRU-59, 추천값)
public class IdentityVerificationRequiredException extends BusinessException {
	public static final String CODE = "ORDER_IDENTITY_VERIFICATION_REQUIRED";

	public IdentityVerificationRequiredException() {
		super(HttpStatus.CONFLICT, CODE, "본인인증을 먼저 해 주세요. 참가자 이름·생년월일·휴대폰은 본인인증 정보로 채웁니다.");
	}
}
