package com.bukang.order.domain.exception;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

import lombok.Getter;

// 결제율 미달로 크루가 파티 만들기 제한 중 (BUK-190). 끝나는 시각을 응답 data.endAt으로 준다 (OrderExceptionHandler)
@Getter
public class CrewPartyRestrictedException extends BusinessException {
	public static final String CODE = "ORDER_CREW_PARTY_RESTRICTED";

	private final LocalDateTime endAt;

	public CrewPartyRestrictedException(LocalDateTime endAt) {
		super(HttpStatus.FORBIDDEN, CODE, "크루가 파티 만들기 제한 중입니다.");
		this.endAt = endAt;
	}
}
