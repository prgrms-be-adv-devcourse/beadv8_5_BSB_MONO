package com.bukang.order.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 고른 키트 옵션이 품절 (판매 수가 준비 수량에 닿음, 11/09 결정). 자리는 결제 기한까지 그대로라 다른 옵션으로 다시 결제한다
public class KitOptionSoldOutException extends BusinessException {
	public static final String CODE = "ORDER_KIT_OPTION_SOLD_OUT";

	public KitOptionSoldOutException(String message) {
		super(HttpStatus.CONFLICT, CODE, message);
	}
}
