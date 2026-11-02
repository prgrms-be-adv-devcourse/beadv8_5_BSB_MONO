package com.bukang.member.crew.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 보낸 사람도 크루장도 아님
public class CrewInviteCancelForbiddenException extends BusinessException {
	public CrewInviteCancelForbiddenException() {
		super(HttpStatus.FORBIDDEN, "CREW_INVITE_CANCEL_FORBIDDEN", "초대를 보낸 사람이나 크루장만 취소할 수 있습니다.");
	}
}
