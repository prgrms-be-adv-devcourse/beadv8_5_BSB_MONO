package com.bukang.member.crew.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 크루장 말고 다른 크루원이 있어 크루를 없앨 수 없음 (BE-032)
public class CrewHasOtherMembersException extends BusinessException {
	public CrewHasOtherMembersException() {
		super(HttpStatus.CONFLICT, "CREW_HAS_OTHER_MEMBERS", "다른 크루원이 있어 크루를 없앨 수 없습니다. 크루장을 넘긴 뒤 탈퇴해주세요.");
	}
}
