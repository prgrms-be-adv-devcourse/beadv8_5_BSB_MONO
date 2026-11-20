package com.bukang.order.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 없는 주문이거나 남의 주문이다. 남의 주문이 있는지도 알려 주지 않도록 둘 다 404로 답한다 (CRU-91)
public class OrderNotFoundException extends BusinessException {
	public static final String CODE = "ORDER_NOT_FOUND";

	public OrderNotFoundException(String message) {
		super(HttpStatus.NOT_FOUND, CODE, message);
	}
}
