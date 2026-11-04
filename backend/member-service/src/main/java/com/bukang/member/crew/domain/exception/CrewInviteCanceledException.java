package com.bukang.member.crew.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 취소된 초대
// 초대를 취소할 때는 409, 초대 링크를 열거나 가입할 때는 410으로 응답한다
public class CrewInviteCanceledException extends BusinessException {
	private CrewInviteCanceledException(HttpStatus status, String message) {
		super(status, "CREW_INVITE_CANCELED", message);
	}

	public static CrewInviteCanceledException whenCanceling() {
		return new CrewInviteCanceledException(HttpStatus.CONFLICT, "이미 취소한 초대입니다.");
	}

	public static CrewInviteCanceledException whenOpeningLink() {
		return new CrewInviteCanceledException(HttpStatus.GONE, "취소된 초대 링크입니다. 새 초대 링크를 받아주세요.");
	}
}
