package com.bukang.order.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 발행 실패(SEND_FAILED)가 아닌 아웃박스 행을 되돌리려 함. 이미 보냈거나 아직 보내는 중이다 (BUK-180)
public class OutboxEventNotFailedException extends BusinessException {
	public static final String CODE = "ORDER_OUTBOX_INVALID_STATUS";

	public OutboxEventNotFailedException(long id, String statusLabel) {
		super(HttpStatus.CONFLICT, CODE, "아웃박스 메시지 " + id + "번은 " + statusLabel + " 상태라 다시 보낼 수 없습니다.");
	}
}
