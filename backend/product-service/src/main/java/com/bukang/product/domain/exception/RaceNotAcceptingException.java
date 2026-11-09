package com.bukang.product.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

/**
 * 접수 기간이 아니거나 판매 중이 아닌(판매 중지·취소 등) 대회의 자리를 확보하려 할 때
 * 개인 자리는 개인 예매 기간, 크루 자리(파티 신청)는 크루 예매 기간을 본다
 */
public class RaceNotAcceptingException extends BusinessException {
	public RaceNotAcceptingException() {
		this("접수 기간이 아닙니다.");
	}

	private RaceNotAcceptingException(String message) {
		super(HttpStatus.CONFLICT, message);
	}

	// 크루 예매 기간이 아니거나 크루 예매가 없는 대회에 크루 자리를 확보하려 할 때
	public static RaceNotAcceptingException crew() {
		return new RaceNotAcceptingException("크루 예매 기간이 아닙니다.");
	}
}
