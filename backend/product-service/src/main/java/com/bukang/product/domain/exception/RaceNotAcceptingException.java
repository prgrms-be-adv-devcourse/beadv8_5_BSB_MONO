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

	// 판매자가 판매를 멈춘 대회 (개인·크루 공통). 기간 문구보다 먼저 알려, 구매자가 멈춘 이유를 알게 한다
	// 대기열 서비스의 등록 거절 문구와 같다. 주문 서비스는 이 409 문구를 그대로 구매자에게 넘긴다
	public static RaceNotAcceptingException stopped() {
		return new RaceNotAcceptingException("판매를 잠시 멈춘 대회입니다.");
	}
}
