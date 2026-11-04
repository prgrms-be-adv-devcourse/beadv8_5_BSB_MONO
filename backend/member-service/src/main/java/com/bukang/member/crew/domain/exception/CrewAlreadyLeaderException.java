package com.bukang.member.crew.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 자기 자신에게 크루장을 넘기려 함
public class CrewAlreadyLeaderException extends BusinessException {
	public CrewAlreadyLeaderException() {
		super(HttpStatus.CONFLICT, "CREW_ALREADY_LEADER", "이미 크루장입니다.");
	}
}
