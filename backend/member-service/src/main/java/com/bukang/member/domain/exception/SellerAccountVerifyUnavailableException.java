package com.bukang.member.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

/**
 * 오픈뱅킹에 닿지 못했거나(시간 초과, 서버 오류) 운영에 키가 없다. 신청자가 잘못한 것이 아니라 잠시 뒤 다시 시도하게 한다 (BUK-50)
 */
public class SellerAccountVerifyUnavailableException extends BusinessException {
	public static final String CODE = "SELLER_ACCOUNT_VERIFY_UNAVAILABLE";
	public static final String MESSAGE = "잠시 뒤 다시 시도해 주세요.";

	public SellerAccountVerifyUnavailableException() {
		super(HttpStatus.SERVICE_UNAVAILABLE, CODE, MESSAGE);
	}
}
