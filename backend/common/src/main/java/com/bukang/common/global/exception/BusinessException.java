package com.bukang.common.global.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/**
 * 도메인 예외의 부모. 응답 상태 코드를 예외가 직접 들고 있어서,
 * 도메인을 추가해도 GlobalExceptionHandler를 고치지 않는다.
 */
@Getter
public abstract class BusinessException extends RuntimeException {
	private final HttpStatus status;

	protected BusinessException(HttpStatus status, String message) {
		super(message);
		this.status = status;
	}
}
