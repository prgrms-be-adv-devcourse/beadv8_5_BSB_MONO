package com.bukang.member.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

/**
 * 회원 한 명이 하루에 계좌 확인을 5번 넘게 했다. 남의 계좌번호로 예금주 이름을 알아내지 못하게 막는다 (BUK-50)
 */
public class SellerAccountVerifyTooManyRequestsException extends BusinessException {
	public static final String CODE = "SELLER_ACCOUNT_VERIFY_TOO_MANY_REQUESTS";
	public static final String MESSAGE = "계좌 확인은 하루 5번까지 할 수 있어요. 내일 다시 시도해 주세요.";

	public SellerAccountVerifyTooManyRequestsException() {
		super(HttpStatus.TOO_MANY_REQUESTS, CODE, MESSAGE);
	}
}
