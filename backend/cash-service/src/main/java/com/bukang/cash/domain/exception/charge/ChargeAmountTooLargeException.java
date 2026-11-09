package com.bukang.cash.domain.exception.charge;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

public class ChargeAmountTooLargeException extends BusinessException {
	public static final String PER_CHARGE_CODE = "CHARGE_LIMIT_PER_CHARGE";
	public static final String PER_DAY_CODE = "CHARGE_LIMIT_PER_DAY";
	public static final String BALANCE_CODE = "CHARGE_LIMIT_BALANCE";

	private ChargeAmountTooLargeException(String code, String message) {
		super(HttpStatus.BAD_REQUEST, code, message);
	}

	// 1회 충전 한도(10만 원) 초과
	public static ChargeAmountTooLargeException perCharge(String message) {
		return new ChargeAmountTooLargeException(PER_CHARGE_CODE, message);
	}

	// 일일 충전 한도(100만 원) 초과
	public static ChargeAmountTooLargeException perDay(String message) {
		return new ChargeAmountTooLargeException(PER_DAY_CODE, message);
	}

	// 지갑 잔액 상한(1000만 원) 초과
	public static ChargeAmountTooLargeException balance(String message) {
		return new ChargeAmountTooLargeException(BALANCE_CODE, message);
	}
}
