package com.bukang.order.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 관리자 주문 조회의 검색 조건이 잘못됨 (닉네임·주문번호 중 하나만 넣어야 함, BUK-26)
public class AdminOrderSearchException extends BusinessException {
	public static final String CODE = "ORDER_INVALID_ADMIN_SEARCH";

	public AdminOrderSearchException(String message) {
		super(HttpStatus.BAD_REQUEST, CODE, message);
	}
}
