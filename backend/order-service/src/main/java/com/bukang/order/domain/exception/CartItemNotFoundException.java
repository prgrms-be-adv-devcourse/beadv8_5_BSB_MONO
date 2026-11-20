package com.bukang.order.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 장바구니에 없는 항목이거나 남의 장바구니 항목이다. 남의 항목이 있는지도 알려 주지 않도록 둘 다 404로 답한다 (CRU-91)
public class CartItemNotFoundException extends BusinessException {
	public static final String CODE = "ORDER_CART_ITEM_NOT_FOUND";

	public CartItemNotFoundException(String message) {
		super(HttpStatus.NOT_FOUND, CODE, message);
	}
}
