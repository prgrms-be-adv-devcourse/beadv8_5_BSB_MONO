package com.bukang.member.crew.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 상품 서비스에 결제율 미달 크루를 묻지 못했다. 매일 저장 작업이 그 달만 건너뛰고 다음 날 다시 묻는다 (BUK-187)
public class CrewPenaltyLookupFailedException extends BusinessException {
	public CrewPenaltyLookupFailedException(String message) {
		super(HttpStatus.SERVICE_UNAVAILABLE, "CREW_PENALTY_LOOKUP_FAILED", message);
	}
}
