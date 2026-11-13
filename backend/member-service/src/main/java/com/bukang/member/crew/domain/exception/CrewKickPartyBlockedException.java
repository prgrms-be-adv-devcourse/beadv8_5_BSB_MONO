package com.bukang.member.crew.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 결제 진행 중·결제 끝 파티에 들어 있는 크루원은 내보낼 수 없다. 모집 중 파티는 막지 않는다 (BUK-102)
public class CrewKickPartyBlockedException extends BusinessException {
	public CrewKickPartyBlockedException() {
		super(HttpStatus.CONFLICT, "CREW_KICK_PARTY_BLOCKED",
			"결제가 진행 중인 파티에 들어 있는 크루원은 내보낼 수 없습니다. 결제가 끝나거나 파티가 끝난 뒤 다시 시도해주세요.");
	}
}
