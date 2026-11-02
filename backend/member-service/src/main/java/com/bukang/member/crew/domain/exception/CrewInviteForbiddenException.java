package com.bukang.member.crew.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 크루원 역할은 초대 링크를 만들거나 목록을 볼 수 없다
public class CrewInviteForbiddenException extends BusinessException {
	public CrewInviteForbiddenException() {
		super(HttpStatus.FORBIDDEN, "CREW_INVITE_FORBIDDEN", "크루장과 운영진만 초대할 수 있습니다.");
	}
}
