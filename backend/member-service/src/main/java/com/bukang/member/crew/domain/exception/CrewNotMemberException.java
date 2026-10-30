package com.bukang.member.crew.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 크루원이 아님
public class CrewNotMemberException extends BusinessException {
	public CrewNotMemberException() {
		super(HttpStatus.FORBIDDEN, "CREW_NOT_MEMBER", "이 크루의 크루원만 이용할 수 있습니다.");
	}
}
