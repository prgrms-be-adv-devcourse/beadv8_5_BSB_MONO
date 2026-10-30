package com.bukang.member.crew.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 토큰이 없거나 초대가 없거나 크루를 없앰
public class CrewInviteNotFoundException extends BusinessException {
	public CrewInviteNotFoundException() {
		super(HttpStatus.NOT_FOUND, "CREW_INVITE_NOT_FOUND", "존재하지 않는 초대 링크입니다.");
	}
}
