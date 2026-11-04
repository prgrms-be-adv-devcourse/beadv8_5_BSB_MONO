package com.bukang.member.crew.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 크루장 자신을 내보내려 함
public class CrewCannotRemoveLeaderException extends BusinessException {
	public CrewCannotRemoveLeaderException() {
		super(HttpStatus.CONFLICT, "CREW_CANNOT_REMOVE_LEADER", "크루장은 내보낼 수 없습니다.");
	}
}
