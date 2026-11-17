package com.bukang.member.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 판매 중인 대회가 있는 판매자는 탈퇴할 수 없다 (CRU-23)
public class WithdrawRaceLeftException extends BusinessException {
	public WithdrawRaceLeftException(String message) {
		super(HttpStatus.CONFLICT, "MEMBER_WITHDRAW_RACE_LEFT", message);
	}
}
