package com.bukang.member.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 지급되지 않은 정산금이 있는 판매자는 탈퇴할 수 없다 (CRU-23)
public class WithdrawPayoutLeftException extends BusinessException {
	public WithdrawPayoutLeftException(String message) {
		super(HttpStatus.CONFLICT, "MEMBER_WITHDRAW_PAYOUT_LEFT", message);
	}
}
