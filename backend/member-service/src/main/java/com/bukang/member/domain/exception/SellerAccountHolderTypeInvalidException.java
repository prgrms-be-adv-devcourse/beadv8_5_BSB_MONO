package com.bukang.member.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

/**
 * 비사업자 판매자가 사업자 명의 계좌를 골랐다 (CRU-80). 은행을 부르기 전에 막는다
 */
public class SellerAccountHolderTypeInvalidException extends BusinessException {
	public SellerAccountHolderTypeInvalidException() {
		super(HttpStatus.BAD_REQUEST, "SELLER_ACCOUNT_HOLDER_TYPE_INVALID", "사업자 명의 계좌는 사업자만 고를 수 있습니다.");
	}
}
