package com.bukang.member.crew.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 크루장은 크루장을 넘긴 뒤에만 떠난다. 혼자 남았으면 크루를 없앤다 (BE-032)
public class CrewLeaderCannotLeaveException extends BusinessException {
	public CrewLeaderCannotLeaveException() {
		super(HttpStatus.CONFLICT, "CREW_LEADER_CANNOT_LEAVE", "크루장은 크루장을 넘긴 뒤 탈퇴할 수 있습니다. 다른 크루원이 없으면 크루를 없애주세요.");
	}
}
