package com.bukang.member.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 크루장인 크루가 있으면 탈퇴할 수 없다 (BE-029)
public class WithdrawCrewLeaderException extends BusinessException {
	public WithdrawCrewLeaderException(String message) {
		super(HttpStatus.CONFLICT, "MEMBER_WITHDRAW_CREW_LEADER", message);
	}
}
