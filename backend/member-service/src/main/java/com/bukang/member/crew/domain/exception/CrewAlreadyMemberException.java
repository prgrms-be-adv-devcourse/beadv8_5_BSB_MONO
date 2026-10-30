package com.bukang.member.crew.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 이미 그 크루의 크루원
public class CrewAlreadyMemberException extends BusinessException {
	public CrewAlreadyMemberException() {
		super(HttpStatus.CONFLICT, "CREW_ALREADY_MEMBER", "이미 가입한 크루입니다.");
	}
}
