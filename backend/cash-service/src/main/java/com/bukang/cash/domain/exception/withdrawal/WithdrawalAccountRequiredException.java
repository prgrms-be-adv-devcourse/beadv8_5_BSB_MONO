package com.bukang.cash.domain.exception.withdrawal;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 계좌로 보낼 몫(기한 지난 몫, 정산 대금)이 있는데 계좌 정보 없이 환급을 신청함. 프론트는 다시 조회해 계좌 입력을 보여 준다
public class WithdrawalAccountRequiredException extends BusinessException {
	public WithdrawalAccountRequiredException(String message) {
		super(HttpStatus.BAD_REQUEST, "WITHDRAWAL_ACCOUNT_REQUIRED", message);
	}
}
