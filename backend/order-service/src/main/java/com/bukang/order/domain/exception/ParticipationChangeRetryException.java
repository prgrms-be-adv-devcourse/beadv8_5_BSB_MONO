package com.bukang.order.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 참가자 정보를 고치는 사이에 [결제하기]가 주문을 만들어, 발송 대기 배송의 주소까지 함께 바꾸려면 다시 시도해야 함
public class ParticipationChangeRetryException extends BusinessException {
	public static final String CODE = "ORDER_PARTICIPATION_CHANGE_RETRY";

	public ParticipationChangeRetryException(String message) {
		super(HttpStatus.CONFLICT, CODE, message);
	}
}
