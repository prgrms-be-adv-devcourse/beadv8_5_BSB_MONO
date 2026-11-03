package com.bukang.order.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;
import com.bukang.order.domain.PartyStatus;

// 파티 상태 때문에 지금 할 수 없는 요청 (BUK-36). code로 프론트가 원인을 나눈다
public class PartyConflictException extends BusinessException {
	public static final String NOT_RECRUITING = "ORDER_PARTY_NOT_RECRUITING";
	public static final String LINK_EXPIRED = "ORDER_PARTY_LINK_EXPIRED";
	public static final String RACE_MISMATCH = "ORDER_PARTY_RACE_MISMATCH";
	public static final String ALREADY_JOINED = "ORDER_PARTY_ALREADY_JOINED";

	private PartyConflictException(String code, String message) {
		super(HttpStatus.CONFLICT, code, message);
	}

	public static PartyConflictException notRecruiting(PartyStatus status) {
		return new PartyConflictException(NOT_RECRUITING,
			"모집 중인 파티가 아니라 파티원을 바꿀 수 없습니다(현재 상태: %s).".formatted(status.getLabel()));
	}

	public static PartyConflictException linkExpired() {
		return new PartyConflictException(LINK_EXPIRED, "참가 링크가 만료됐습니다. 파티장에게 새 링크를 받아주세요.");
	}

	public static PartyConflictException raceMismatch() {
		return new PartyConflictException(RACE_MISMATCH, "이 파티의 대회 종목만 고를 수 있습니다.");
	}

	public static PartyConflictException alreadyJoined() {
		return new PartyConflictException(ALREADY_JOINED, "이미 이 파티에 합류했습니다.");
	}
}
