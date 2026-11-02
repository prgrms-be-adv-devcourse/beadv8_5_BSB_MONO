package com.bukang.member.crew.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 24시간이 지난 초대
// 초대를 취소할 때는 409, 초대 링크를 열거나 가입할 때는 410으로 응답한다
public class CrewInviteExpiredException extends BusinessException {
	private CrewInviteExpiredException(HttpStatus status, String message) {
		super(status, "CREW_INVITE_EXPIRED", message);
	}

	public static CrewInviteExpiredException whenCanceling() {
		return new CrewInviteExpiredException(HttpStatus.CONFLICT, "유효 시간이 지난 초대입니다.");
	}

	public static CrewInviteExpiredException whenOpeningLink() {
		return new CrewInviteExpiredException(HttpStatus.GONE, "초대 링크의 유효 시간(24시간)이 지났습니다. 새 초대 링크를 받아주세요.");
	}
}
