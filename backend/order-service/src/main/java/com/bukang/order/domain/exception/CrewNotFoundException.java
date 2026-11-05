package com.bukang.order.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 없거나 없앤 크루 (회원 서비스 CREW_NOT_FOUND)
public class CrewNotFoundException extends BusinessException {
	public static final String CODE = "ORDER_CREW_NOT_FOUND";

	public CrewNotFoundException(String message) {
		super(HttpStatus.NOT_FOUND, CODE, message);
	}
}
