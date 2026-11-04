package com.bukang.member.crew.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 내보내거나 크루장을 넘길 대상이 이 크루의 크루원이 아님
public class CrewMemberNotFoundException extends BusinessException {
	public CrewMemberNotFoundException() {
		super(HttpStatus.NOT_FOUND, "CREW_MEMBER_NOT_FOUND", "이 크루의 크루원이 아닙니다.");
	}
}
