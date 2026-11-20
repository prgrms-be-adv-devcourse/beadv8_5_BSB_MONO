package com.bukang.order.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 없는 참가 신청이거나 남의 참가 신청이다. 남의 신청이 있는지도 알려 주지 않도록 둘 다 404로 답한다 (CRU-91)
public class ParticipationNotFoundException extends BusinessException {
	public static final String CODE = "ORDER_PARTICIPATION_NOT_FOUND";

	public ParticipationNotFoundException(String message) {
		super(HttpStatus.NOT_FOUND, CODE, message);
	}
}
