package com.bukang.member.crew.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 주문 서비스에 파티 상태를 묻지 못하면 나가기·내보내기를 하지 않는다. 막는 쪽으로 실패한다 (BUK-102)
public class CrewPartyCheckFailedException extends BusinessException {
	public CrewPartyCheckFailedException(String message) {
		super(HttpStatus.SERVICE_UNAVAILABLE, "CREW_PARTY_CHECK_FAILED", message);
	}
}
