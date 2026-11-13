package com.bukang.order.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 내부 API로 되돌리기를 요청한 아웃박스 행이 없음 (BUK-180)
public class OutboxEventNotFoundException extends BusinessException {
	public static final String CODE = "ORDER_OUTBOX_NOT_FOUND";

	public OutboxEventNotFoundException(long id) {
		super(HttpStatus.NOT_FOUND, CODE, "아웃박스 메시지 " + id + "번이 없습니다.");
	}
}
