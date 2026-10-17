package com.bukang.product.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

/**
 * 정산용 대회 조회 기간이 잘못됨 (시작일이 종료일보다 늦거나, 한 번에 조회할 수 있는 기간을 넘음)
 */
public class RacePeriodException extends BusinessException {
	public RacePeriodException(String message) {
		super(HttpStatus.BAD_REQUEST, message);
	}
}
