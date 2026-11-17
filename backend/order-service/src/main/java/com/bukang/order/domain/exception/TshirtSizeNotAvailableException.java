package com.bukang.order.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 티셔츠를 주는 종목인데 사이즈를 고르지 않았거나, 종목이 주지 않는 사이즈를 골랐음
public class TshirtSizeNotAvailableException extends BusinessException {
	public static final String CODE = "ORDER_TSHIRT_SIZE_NOT_AVAILABLE";
	public static final String REQUIRED_CODE = "ORDER_TSHIRT_SIZE_REQUIRED";
	public static final String LOCKED_CODE = "ORDER_TSHIRT_SIZE_LOCKED";

	public TshirtSizeNotAvailableException(String message) {
		this(CODE, message);
	}

	private TshirtSizeNotAvailableException(String code, String message) {
		super(HttpStatus.BAD_REQUEST, code, message);
	}

	// 티셔츠를 주는 종목인데 사이즈를 고르지 않음
	public static TshirtSizeNotAvailableException required(String message) {
		return new TshirtSizeNotAvailableException(REQUIRED_CODE, message);
	}

	// 결제한 참가권의 사이즈를 바꾸려 함
	public static TshirtSizeNotAvailableException locked(String message) {
		return new TshirtSizeNotAvailableException(LOCKED_CODE, message);
	}
}
