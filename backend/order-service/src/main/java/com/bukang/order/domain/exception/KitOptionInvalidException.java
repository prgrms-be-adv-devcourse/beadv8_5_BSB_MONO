package com.bukang.order.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 고른 키트 옵션이 맞지 않음: 키트 없는 종목에 옵션, 다른 대회·없는 옵션, 옵션 이름 빠짐·겹침
public class KitOptionInvalidException extends BusinessException {
	public static final String CODE = "ORDER_KIT_OPTION_INVALID";

	public KitOptionInvalidException(String message) {
		super(HttpStatus.BAD_REQUEST, CODE, message);
	}
}
