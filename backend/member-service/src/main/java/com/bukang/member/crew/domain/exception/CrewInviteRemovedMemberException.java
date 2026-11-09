package com.bukang.member.crew.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 크루에서 내보낸 회원이 내보내기 전에 만든 초대 링크를 열거나 가입하려 함 (BUK-135)
public class CrewInviteRemovedMemberException extends BusinessException {
	public CrewInviteRemovedMemberException() {
		super(HttpStatus.GONE, "CREW_INVITE_REMOVED_MEMBER",
			"크루에서 내보낸 회원은 이 초대 링크로 가입할 수 없습니다. 크루장에게 새 초대 링크를 받아주세요.");
	}
}
