package com.bukang.member.crew.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 모집 중·결제 진행 중·결제 끝 파티에 들어 있으면 스스로 나갈 수 없다 (BUK-102)
public class CrewLeavePartyBlockedException extends BusinessException {
	public CrewLeavePartyBlockedException() {
		super(HttpStatus.CONFLICT, "CREW_LEAVE_PARTY_BLOCKED", "참여 중인 파티가 있어 크루를 나갈 수 없습니다. 파티에서 나온 뒤 다시 시도해주세요.");
	}
}
