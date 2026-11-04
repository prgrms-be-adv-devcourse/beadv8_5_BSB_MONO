package com.bukang.order.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 없는 파티이거나, 참가 링크가 없음(다시 만들어 옛 링크가 됨). 링크를 다시 받아야 한다
public class PartyNotFoundException extends BusinessException {
	public static final String CODE = "ORDER_PARTY_NOT_FOUND";

	public PartyNotFoundException(String message) {
		super(HttpStatus.NOT_FOUND, CODE, message);
	}
}
