package com.bukang.member.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

/**
 * 은행이 계좌와 명의가 맞지 않다고 답했거나, 본인 명의 계좌의 예금주 이름이 본인인증 이름과 다르다 (BUK-50)
 */
public class SellerAccountNotVerifiedException extends BusinessException {
	public static final String CODE = "SELLER_ACCOUNT_NOT_VERIFIED";
	public static final String MESSAGE = "예금주 정보가 맞지 않아요. 계좌번호와 계좌 명의를 확인해 주세요.";

	public SellerAccountNotVerifiedException() {
		super(HttpStatus.BAD_REQUEST, CODE, MESSAGE);
	}
}
