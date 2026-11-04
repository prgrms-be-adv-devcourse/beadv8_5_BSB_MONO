package com.bukang.member.crew.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 크루장만 하는 일(내보내기, 크루장 넘기기, 없애기, 이름 바꾸기)을 운영진·크루원이 하려 함
public class CrewLeaderOnlyException extends BusinessException {
	public CrewLeaderOnlyException() {
		super(HttpStatus.FORBIDDEN, "CREW_LEADER_ONLY", "크루장만 할 수 있습니다.");
	}
}
