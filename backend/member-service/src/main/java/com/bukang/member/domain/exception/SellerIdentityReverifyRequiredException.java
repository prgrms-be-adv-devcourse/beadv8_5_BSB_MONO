package com.bukang.member.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

/**
 * 본인 명의 계좌인데 회원의 이름·생년월일 해시가 비어 있다 (BUK-50)
 * 이름·생년월일을 바꿀 수 있던 때 가입한 회원은 지금 값이 본인인증 값이라고 믿을 수 없다. 휴대폰 번호를 다시 인증해 해시를 채운 뒤 신청하게 한다
 */
public class SellerIdentityReverifyRequiredException extends BusinessException {
	public static final String CODE = "SELLER_IDENTITY_REVERIFY_REQUIRED";
	public static final String MESSAGE = "본인 명의 계좌는 휴대폰 번호를 다시 인증한 뒤 신청해 주세요.";

	public SellerIdentityReverifyRequiredException() {
		super(HttpStatus.BAD_REQUEST, CODE, MESSAGE);
	}
}
