package com.bukang.member.crew.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 이미 가입에 쓰인 초대
// 초대를 취소할 때는 409, 초대 링크를 열거나 가입할 때는 410으로 응답한다
public class CrewInviteAlreadyUsedException extends BusinessException {
	private CrewInviteAlreadyUsedException(HttpStatus status, String message) {
		super(status, "CREW_INVITE_ALREADY_USED", message);
	}

	public static CrewInviteAlreadyUsedException whenCanceling() {
		return new CrewInviteAlreadyUsedException(HttpStatus.CONFLICT, "이미 가입에 쓰인 초대는 취소할 수 없습니다.");
	}

	public static CrewInviteAlreadyUsedException whenOpeningLink() {
		return new CrewInviteAlreadyUsedException(HttpStatus.GONE, "이미 가입에 쓰인 초대 링크입니다. 새 초대 링크를 받아주세요.");
	}
}
