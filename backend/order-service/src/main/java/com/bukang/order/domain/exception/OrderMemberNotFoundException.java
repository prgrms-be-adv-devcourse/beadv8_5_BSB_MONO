package com.bukang.order.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 관리자가 찾은 닉네임의 회원이 없음 (BUK-26). 회원은 있는데 주문이 없으면 빈 목록이다
public class OrderMemberNotFoundException extends BusinessException {
	public static final String CODE = "ORDER_MEMBER_NOT_FOUND";

	public OrderMemberNotFoundException(String message) {
		super(HttpStatus.NOT_FOUND, CODE, message);
	}
}
